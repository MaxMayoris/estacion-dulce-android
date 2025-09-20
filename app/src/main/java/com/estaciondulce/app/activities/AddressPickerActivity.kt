package com.estaciondulce.app.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.estaciondulce.app.BuildConfig
import com.estaciondulce.app.R
import com.estaciondulce.app.models.parcelables.Address
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AddressComponent
import java.io.IOException
import java.util.Locale

/**
 * Activity for picking an address using Google Maps with Places API autocomplete.
 * Uses the new Places API for better integration and performance.
 */
class AddressPickerActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private lateinit var addressText: android.widget.TextView
    private lateinit var confirmButton: com.google.android.material.button.MaterialButton
    private lateinit var cancelButton: com.google.android.material.button.MaterialButton
    private lateinit var searchEditText: com.google.android.material.textfield.TextInputEditText
    private lateinit var addressSuggestionsRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedMarker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private var geocoder: Geocoder? = null
    private lateinit var placesClient: PlacesClient
    private var currentAddress: android.location.Address? = null
    private var addressToEdit: Address? = null
    private var isEditMode = false
    private var autocompleteSessionToken: AutocompleteSessionToken? = null
    private lateinit var addressSuggestionAdapter: com.estaciondulce.app.adapters.AddressSuggestionAdapter

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_ADDRESS_LABEL = "address_label"
        const val EXTRA_EDIT_MODE = "edit_mode"
        const val EXTRA_ADDRESS_TO_EDIT = "address_to_edit"
        const val EXTRA_DRAFT_MODE = "draft_mode"
        const val RESULT_ADDRESS = "result_address"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_picker)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Seleccionar Dirección"

        addressText = findViewById(R.id.addressText)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
        searchEditText = findViewById(R.id.searchEditText)
        addressSuggestionsRecyclerView = findViewById(R.id.addressSuggestionsRecyclerView)

        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        
        if (apiKey.isEmpty()) {
            CustomToast.showError(this, "Google Maps API key no configurada. Configure las variables de entorno.")
            finish()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        if (!Places.isInitialized()) {
            Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY)
        }
        placesClient = Places.createClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        val isDraftMode = intent.getBooleanExtra(EXTRA_DRAFT_MODE, false)
        
        if (isEditMode) {
            @Suppress("DEPRECATION")
            addressToEdit = intent.getParcelableExtra(EXTRA_ADDRESS_TO_EDIT)
        } else if (isDraftMode) {
        }

        setupSearchFunctionality()

        setupUI()
    }

    private fun setupSearchFunctionality() {
        searchEditText.hint = "Buscar dirección"

        addressSuggestionsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        addressSuggestionAdapter = com.estaciondulce.app.adapters.AddressSuggestionAdapter { prediction ->
            handlePredictionSelection(prediction)
            hideSuggestions()
        }
        addressSuggestionsRecyclerView.adapter = addressSuggestionAdapter

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()
                if (!query.isNullOrEmpty() && ::map.isInitialized) {
                    if (isCoordinateFormat(query)) {
                        handleCoordinateSearch(query)
                        hideSuggestions()
                    } else if (query.length >= 3) {
                        performAutocompleteSearch(query)
                    } else {
                        hideSuggestions()
                    }
                } else {
                    hideSuggestions()
                }
            }
        })

        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideSuggestions()
            }
        }
    }

    private fun performAutocompleteSearch(query: String) {
        autocompleteSessionToken = AutocompleteSessionToken.newInstance()
        
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(autocompleteSessionToken)
            .build()
        
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                if (predictions.isNotEmpty()) {
                    addressSuggestionAdapter.updateSuggestions(predictions)
                    showSuggestions()
                } else {
                    hideSuggestions()
                }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("AddressPicker", "Autocomplete search failed: ${exception.message}")
                hideSuggestions()
            }
    }
    
    private fun handlePredictionSelection(prediction: AutocompletePrediction) {
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS_COMPONENTS
        )
        
        val request = FetchPlaceRequest.builder(prediction.placeId, placeFields)
            .setSessionToken(autocompleteSessionToken)
            .build()
        
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                handlePlaceSelection(place)
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("AddressPicker", "Place fetch failed: ${exception.message}")
                CustomToast.showError(this, "Error al obtener detalles del lugar: ${exception.message}")
            }
    }
    
    private fun handlePlaceSelection(place: Place) {
        val latLng = place.latLng
        if (latLng != null) {
            val address = place.address ?: place.name
            addressText.text = address
            
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            selectedLatLng = latLng
            updateMarker(latLng)
            
            currentAddress = createAddressFromPlace(place)
        }
    }

    private fun showSuggestions() {
        addressSuggestionsRecyclerView.visibility = android.view.View.VISIBLE
    }

    private fun hideSuggestions() {
        addressSuggestionsRecyclerView.visibility = android.view.View.GONE
    }

    private fun isCoordinateFormat(text: String): Boolean {
        val coordinatePattern = Regex("^-?\\d+\\.\\d+\\s*,\\s*-?\\d+\\.\\d+$")
        return coordinatePattern.matches(text)
    }

    private fun handleCoordinateSearch(coordinateText: String) {
        try {
            val parts = coordinateText.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDouble()
                val lng = parts[1].trim().toDouble()
                
                
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    val latLng = LatLng(lat, lng)
                    
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    selectedLatLng = latLng
                    updateMarker(latLng)
                    
                    reverseGeocode(latLng)
                    
                    CustomToast.showSuccess(this, "Coordenadas encontradas: $lat, $lng")
                } else {
                    android.util.Log.w("AddressPicker", "Invalid coordinate ranges: lat=$lat, lng=$lng")
                    CustomToast.showError(this, "Coordenadas inválidas. Lat: -90 a 90, Lng: -180 a 180")
                }
            } else {
                android.util.Log.w("AddressPicker", "Invalid coordinate format: $coordinateText")
                CustomToast.showError(this, "Formato inválido. Use: lat, lng")
            }
        } catch (e: NumberFormatException) {
            android.util.Log.e("AddressPicker", "Error parsing coordinates: $coordinateText", e)
            CustomToast.showError(this, "Error al procesar coordenadas: $coordinateText")
        }
    }

    private fun setupUI() {
        confirmButton.isEnabled = false
        confirmButton.text = "Cargando mapa..."

        confirmButton.setOnClickListener {
            confirmAddressSelection()
        }

        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        try {
            map = googleMap
            map.setOnMapClickListener(this)
            
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true


            confirmButton.isEnabled = true
            confirmButton.text = "Confirmar"

            if (isEditMode && addressToEdit != null) {
                loadExistingAddress()
            } else {
                val defaultLocation = LatLng(-31.5375, -68.5364) // San Juan Capital coordinates
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
            }
        } catch (e: Exception) {
            android.util.Log.e("AddressPicker", "Error in onMapReady: ${e.message}", e)
            CustomToast.showError(this, "Error al inicializar el mapa: ${e.message}")
        }
    }

    override fun onMapClick(latLng: LatLng) {
        selectedLatLng = latLng
        updateMarker(latLng)
        
        addressText.text = "Obteniendo dirección..."
        
        reverseGeocode(latLng)
    }

    private fun updateMarker(latLng: LatLng) {
        selectedMarker?.remove()
        selectedMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Dirección seleccionada")
        )
    }

    private fun reverseGeocode(latLng: LatLng) {
        
        if (geocoder == null) {
            android.util.Log.e("AddressPicker", "Geocoder is null")
            addressText.text = "Error: Geocoder no disponible"
            return
        }

        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (addressText.text == "Obteniendo dirección...") {
                android.util.Log.w("AddressPicker", "Geocoding timeout, showing coordinates")
                addressText.text = "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 5000) // 5 second timeout

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder!!.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    runOnUiThread {
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            currentAddress = address
                            val formattedAddress = buildFormattedAddress(address)
                            addressText.text = formattedAddress
                        } else {
                            android.util.Log.w("AddressPicker", "No addresses found")
                            addressText.text = "No se pudo obtener la dirección"
                        }
                    }
                }
            } else {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                @Suppress("DEPRECATION")
                val addresses = geocoder!!.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    currentAddress = address
                    val formattedAddress = buildFormattedAddress(address)
                    addressText.text = formattedAddress
                } else {
                    android.util.Log.w("AddressPicker", "No addresses found")
                    addressText.text = "No se pudo obtener la dirección"
                }
            }
        } catch (e: IOException) {
            android.util.Log.e("AddressPicker", "IOException in reverseGeocode: ${e.message}", e)
            timeoutHandler.removeCallbacks(timeoutRunnable)
            addressText.text = "Error al obtener la dirección"
        } catch (e: Exception) {
            android.util.Log.e("AddressPicker", "Exception in reverseGeocode: ${e.message}", e)
            timeoutHandler.removeCallbacks(timeoutRunnable)
            addressText.text = "Error al obtener la dirección"
        }
    }

    private fun buildFormattedAddress(address: android.location.Address): String {
        address.getAddressLine(0)?.let { return it }
        
        val parts = mutableListOf<String>()
        
        val addressLine = address.getAddressLine(0) ?: ""
        if (addressLine.isNotEmpty()) {
            val addressParts = addressLine.split(",").map { it.trim() }
            if (addressParts.isNotEmpty()) {
                val streetName = addressParts[0]
                parts.add(streetName)
            }
        }
        
        address.locality?.let { parts.add(it) }
        address.adminArea?.let { parts.add(it) }
        
        return if (parts.isEmpty()) {
            "Ubicación seleccionada"
        } else {
            parts.joinToString(", ")
        }
    }

    private fun createAddressFromPlace(place: Place): android.location.Address {
        val address = android.location.Address(Locale.getDefault())
        
        place.latLng?.let { latLng ->
            address.latitude = latLng.latitude
            address.longitude = latLng.longitude
        }
        
        place.id?.let { placeId ->
            address.featureName = "PLACE_ID:$placeId"
        }
        
        place.addressComponents?.asList()?.let { components ->
            for (component in components) {
                val types = component.types
                val shortName = component.shortName
                val name = component.name
                
                when {
                    types.contains("street_number") -> address.subThoroughfare = shortName
                    types.contains("route") -> address.thoroughfare = shortName
                    types.contains("locality") -> address.locality = name
                    types.contains("administrative_area_level_1") -> address.adminArea = name
                    types.contains("administrative_area_level_2") -> address.subAdminArea = name
                    types.contains("country") -> address.countryName = name
                    types.contains("postal_code") -> address.postalCode = shortName
                }
            }
        }
        
        if (address.locality.isNullOrEmpty()) {
            place.addressComponents?.asList()?.let { components ->
                for (component in components) {
                    val types = component.types
                    val name = component.name
                    
                    when {
                        types.contains("sublocality") && address.locality.isNullOrEmpty() -> address.locality = name
                        types.contains("sublocality_level_1") && address.locality.isNullOrEmpty() -> address.locality = name
                        types.contains("locality") -> address.locality = name
                    }
                }
            }
        }
        
        address.setAddressLine(0, place.address ?: place.name)
        
        return address
    }

    private fun createAddressFromGeocoder(address: android.location.Address): Address {
        val placeId = if (address.featureName?.startsWith("PLACE_ID:") == true) {
            address.featureName?.substringAfter("PLACE_ID:") ?: ""
        } else {
            ""
        }
        
        val streetParts = mutableListOf<String>()
        
        val addressLine = address.getAddressLine(0) ?: ""
        var streetName: String? = null
        
        if (addressLine.isNotEmpty()) {
            val parts = addressLine.split(",").map { it.trim() }
            if (parts.isNotEmpty()) {
                streetName = parts[0]
            }
        }
        
        if (streetName.isNullOrEmpty()) {
            streetName = address.thoroughfare
            if (streetName.isNullOrEmpty()) {
                streetName = address.featureName
                if (streetName?.startsWith("PLACE_ID:") == true) {
                    streetName = null
                }
            }
        }
        
        streetName?.let { streetParts.add(it) }
        val completeStreet = streetParts.joinToString(" ")
        
        var city: String? = address.locality
        if (!city.isNullOrEmpty() && city.matches(Regex("^[A-Z]{2,4}$"))) {
            city = null
        }
        
        if (city.isNullOrEmpty()) {
            city = address.subLocality
            if (!city.isNullOrEmpty() && city.matches(Regex("^[A-Z]{2,4}$"))) {
                city = null
            }
            
            if (city.isNullOrEmpty()) {
                city = address.subAdminArea
                if ((city.isNullOrEmpty() || city.matches(Regex("^[A-Z]{2,4}$"))) && addressLine.isNotEmpty()) {
                    val parsedAddress = parseRawAddress(addressLine)
                    city = parsedAddress.city
                    
                    if (city.isNullOrEmpty()) {
                        val parts = addressLine.split(",").map { it.trim() }
                        if (parts.size >= 2) {
                            city = parts[parts.size - 3]
                        }
                    }
                }
            }
        }
        
        return Address(
            label = "Dirección",
            rawAddress = address.getAddressLine(0) ?: "",
            formattedAddress = buildFormattedAddress(address),
            placeId = placeId,
            latitude = address.latitude,
            longitude = address.longitude,
            street = completeStreet,
            city = city,
            state = address.adminArea,
            postalCode = address.postalCode,
            country = address.countryName
        )
    }

    private fun loadExistingAddress() {
        addressToEdit?.let { address ->
            
            addressText.text = address.formattedAddress
            
            if (address.latitude != null && address.longitude != null) {
                val latLng = LatLng(address.latitude, address.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                selectedLatLng = latLng
                updateMarker(latLng)
                
                val androidAddress = android.location.Address(Locale.getDefault())
                androidAddress.latitude = address.latitude
                androidAddress.longitude = address.longitude
                androidAddress.setAddressLine(0, address.formattedAddress)
                androidAddress.thoroughfare = address.street
                androidAddress.locality = address.city
                androidAddress.adminArea = address.state
                androidAddress.postalCode = address.postalCode
                androidAddress.countryName = address.country
                currentAddress = androidAddress
            }
        }
    }

    private fun confirmAddressSelection() {
        try {
            if (!::map.isInitialized) {
                CustomToast.showWarning(this, "El mapa aún se está cargando. Espere un momento.")
                return
            }

            val latLng = selectedLatLng
            if (latLng == null) {
                CustomToast.showWarning(this, "Seleccione una ubicación en el mapa")
                return
            }

            val addressTextValue = addressText.text.toString()
            if (addressTextValue.isEmpty() || addressTextValue == "No se pudo obtener la dirección" || addressTextValue == "Error al obtener la dirección") {
                CustomToast.showError(this, "No se pudo obtener la dirección de la ubicación seleccionada")
                return
            }

            val address = if (currentAddress != null) {
                createAddressFromGeocoder(currentAddress!!)
            } else {
                val basicAddress = Address(
                    label = "Dirección",
                    rawAddress = addressTextValue,
                    formattedAddress = addressTextValue,
                    placeId = "",
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                formatAddressForDisplay(basicAddress)
            }
            
            val finalAddress = if (isEditMode && addressToEdit != null) {
                address.copy(
                    id = addressToEdit!!.id,
                    label = addressToEdit!!.label
                )
            } else {
                val label = intent.getStringExtra(EXTRA_ADDRESS_LABEL) ?: "Dirección"
                address.copy(label = label)
            }

            val formattedFinalAddress = formatAddressForDisplay(finalAddress)


            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_ADDRESS, formattedFinalAddress)
            setResult(RESULT_OK, resultIntent)
            
            finish()
        } catch (e: Exception) {
            android.util.Log.e("AddressPicker", "Error in confirmAddressSelection: ${e.message}", e)
            CustomToast.showError(this, "Error al confirmar la dirección: ${e.message}")
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    selectedLatLng = currentLatLng
                    updateMarker(currentLatLng)
                    reverseGeocode(currentLatLng)
                }
            }
        } else {
            requestLocationPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                val defaultLocation = LatLng(-31.5375, -68.5364) // San Juan Capital coordinates
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
                CustomToast.showError(this, "Permiso de ubicación denegado. Mostrando ubicación por defecto.")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun parseRawAddress(rawAddress: String): ParsedAddress {
        val parts = rawAddress.split(",").map { it.trim() }
        
        val filteredParts = parts.filter { part ->
            !part.matches(Regex("^[A-Z]?\\d{4,5}$"))
        }
        
        val cleanedParts = filteredParts.map { part ->
            part.replace(Regex("\\s+\\d{4,5}$"), "")
        }
        
        return when (cleanedParts.size) {
            1 -> ParsedAddress(
                street = cleanedParts[0],
                city = null,
                state = null,
                country = null
            )
            2 -> ParsedAddress(
                street = cleanedParts[0],
                city = cleanedParts[1],
                state = null,
                country = null
            )
            3 -> ParsedAddress(
                street = cleanedParts[0],
                city = cleanedParts[1],
                state = cleanedParts[2],
                country = null
            )
            else -> ParsedAddress(
                street = cleanedParts.getOrNull(0),
                city = cleanedParts.getOrNull(cleanedParts.size - 2),
                state = cleanedParts.getOrNull(cleanedParts.size - 1),
                country = null
            )
        }
    }
    
    private data class ParsedAddress(
        val street: String?,
        val city: String?,
        val state: String?,
        val country: String?
    )

    private fun formatAddressForDisplay(address: Address): Address {
        val parts = mutableListOf<String>()
        
        address.street?.let { parts.add(it) }
        address.city?.let { parts.add(it) }
        address.state?.let { parts.add(it) }
        
        val formattedAddress = parts.joinToString(", ")
        
        return address.copy(formattedAddress = formattedAddress)
    }
}
