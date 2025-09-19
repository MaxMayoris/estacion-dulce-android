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

    // UI elements
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

        // Initialize UI elements
        addressText = findViewById(R.id.addressText)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
        searchEditText = findViewById(R.id.searchEditText)
        addressSuggestionsRecyclerView = findViewById(R.id.addressSuggestionsRecyclerView)

        // Check if Google Maps API key is configured
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        
        if (apiKey.isEmpty()) {
            CustomToast.showError(this, "Google Maps API key no configurada. Configure las variables de entorno.")
            finish()
            return
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(this, BuildConfig.GOOGLE_MAPS_API_KEY)
        }
        placesClient = Places.createClient(this)

        // Set up map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Check if we're in edit mode or draft mode
        isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        val isDraftMode = intent.getBooleanExtra(EXTRA_DRAFT_MODE, false)
        
        if (isEditMode) {
            @Suppress("DEPRECATION")
            addressToEdit = intent.getParcelableExtra(EXTRA_ADDRESS_TO_EDIT)
        } else if (isDraftMode) {
        }

        // Set up search functionality
        setupSearchFunctionality()

        // Set up UI
        setupUI()
    }

    private fun setupSearchFunctionality() {
        // Set hint text in Spanish
        searchEditText.hint = "Buscar dirección"

        // Initialize RecyclerView for suggestions
        addressSuggestionsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        addressSuggestionAdapter = com.estaciondulce.app.adapters.AddressSuggestionAdapter { prediction ->
            handlePredictionSelection(prediction)
            hideSuggestions()
        }
        addressSuggestionsRecyclerView.adapter = addressSuggestionAdapter

        // Add text change listener for autocomplete
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim()
                if (!query.isNullOrEmpty() && ::map.isInitialized) {
                    // Check if the text looks like coordinates (lat,lng format)
                    if (isCoordinateFormat(query)) {
                        handleCoordinateSearch(query)
                        hideSuggestions()
                    } else if (query.length >= 3) {
                        // Perform autocomplete search only with 3+ characters
                        performAutocompleteSearch(query)
                    } else {
                        hideSuggestions()
                    }
                } else {
                    hideSuggestions()
                }
            }
        })

        // Hide suggestions when clicking outside
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideSuggestions()
            }
        }
    }

    private fun performAutocompleteSearch(query: String) {
        // Create a new session token for this search
        autocompleteSessionToken = AutocompleteSessionToken.newInstance()
        
        // Create the request
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(autocompleteSessionToken)
            .build()
        
        // Perform the search
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val predictions = response.autocompletePredictions
                if (predictions.isNotEmpty()) {
                    // Show all predictions in dropdown
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
        // Fetch detailed place information
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
            // Update UI
            val address = place.address ?: place.name
            addressText.text = address
            
            // Update map
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            selectedLatLng = latLng
            updateMarker(latLng)
            
            // Create Address object from Place data
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
        // Check for patterns like: -31.5375, -68.5364 or -31.5375,-68.5364 or -31.538599014282227,-68.5250015258789
        val coordinatePattern = Regex("^-?\\d+\\.\\d+\\s*,\\s*-?\\d+\\.\\d+$")
        return coordinatePattern.matches(text)
    }

    private fun handleCoordinateSearch(coordinateText: String) {
        try {
            val parts = coordinateText.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDouble()
                val lng = parts[1].trim().toDouble()
                
                
                // Validate coordinate ranges
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    val latLng = LatLng(lat, lng)
                    
                    // Update map
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    selectedLatLng = latLng
                    updateMarker(latLng)
                    
                    // Reverse geocode to get address
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
        // Disable confirm button initially
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
            
            // Enable zoom controls
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true


            // Enable confirm button
            confirmButton.isEnabled = true
            confirmButton.text = "Confirmar"

            // Check if we're in edit mode and load existing address
            if (isEditMode && addressToEdit != null) {
                loadExistingAddress()
            } else {
                // For new addresses, always show San Juan, Argentina by default
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
        
        // Update address text to show "Cargando..." while geocoding
        addressText.text = "Obteniendo dirección..."
        
        // Start reverse geocoding
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

        // Set a timeout to ensure UI updates even if geocoding fails
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
                    // Cancel timeout since we got a response
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
                // Cancel timeout since we're using synchronous API
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
        // Use getAddressLine(0) if available, as it already contains the complete formatted address
        address.getAddressLine(0)?.let { return it }
        
        // Fallback: build address from individual components
        val parts = mutableListOf<String>()
        
        // Add street address: "street" "street number"
        val streetParts = mutableListOf<String>()
        address.thoroughfare?.let { streetParts.add(it) } // Street name
        address.subThoroughfare?.let { streetParts.add(it) } // Street number
        if (streetParts.isNotEmpty()) {
            parts.add(streetParts.joinToString(" "))
        }
        
        // Add city (municipio), state (provincia) - no postal code or country needed
        address.locality?.let { parts.add(it) } // City/Municipio
        address.adminArea?.let { parts.add(it) } // State/Provincia
        
        return parts.joinToString(", ")
    }

    private fun createAddressFromPlace(place: Place): android.location.Address {
        val address = android.location.Address(Locale.getDefault())
        
        // Set basic info
        place.latLng?.let { latLng ->
            address.latitude = latLng.latitude
            address.longitude = latLng.longitude
        }
        
        // Store placeId in a custom field (we'll use featureName)
        place.id?.let { placeId ->
            address.featureName = "PLACE_ID:$placeId"
        }
        
        // Extract components from Place using the new API
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
        
        // Ensure city is set - try different approaches
        if (address.locality.isNullOrEmpty()) {
            // Try to get city from subLocality or other fields
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
        
        // Set formatted address
        address.setAddressLine(0, place.address ?: place.name)
        
        return address
    }

    private fun createAddressFromGeocoder(address: android.location.Address): Address {
        // Extract placeId if it was stored in featureName
        val placeId = if (address.featureName?.startsWith("PLACE_ID:") == true) {
            address.featureName?.substringAfter("PLACE_ID:") ?: ""
        } else {
            "" // Not available from Geocoder
        }
        
        // Build complete street address (name + number)
        val streetParts = mutableListOf<String>()
        address.thoroughfare?.let { streetParts.add(it) } // Street name
        address.subThoroughfare?.let { streetParts.add(it) } // Street number
        val completeStreet = streetParts.joinToString(" ")
        
        return Address(
            label = "Dirección", // Default label
            rawAddress = address.getAddressLine(0) ?: "",
            formattedAddress = buildFormattedAddress(address),
            placeId = placeId,
            latitude = address.latitude,
            longitude = address.longitude,
            street = completeStreet,
            city = address.locality,
            state = address.adminArea,
            postalCode = address.postalCode,
            country = address.countryName
        )
    }

    private fun loadExistingAddress() {
        addressToEdit?.let { address ->
            
            // Set the address text
            addressText.text = address.formattedAddress
            
            // Move camera to existing location
            if (address.latitude != null && address.longitude != null) {
                val latLng = LatLng(address.latitude, address.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                selectedLatLng = latLng
                updateMarker(latLng)
                
                // Create currentAddress for detailed info
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
            // Check if map is initialized
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

            // Create address object with detailed information
            val address = if (currentAddress != null) {
                createAddressFromGeocoder(currentAddress!!)
            } else {
                // Fallback if no detailed address info - create a basic address and format it
                val basicAddress = Address(
                    label = "Dirección",
                    rawAddress = addressTextValue,
                    formattedAddress = addressTextValue,
                    placeId = "",
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                // Apply formatting to the fallback address
                formatAddressForDisplay(basicAddress)
            }
            
            // If we're in edit mode, preserve the original ID and label
            val finalAddress = if (isEditMode && addressToEdit != null) {
                address.copy(
                    id = addressToEdit!!.id,
                    label = addressToEdit!!.label
                )
            } else {
                // For draft mode or new addresses, use the label from intent
                val label = intent.getStringExtra(EXTRA_ADDRESS_LABEL) ?: "Dirección"
                address.copy(label = label)
            }

            // Always apply formatting to the final address before returning
            val formattedFinalAddress = formatAddressForDisplay(finalAddress)


            // Return result
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
                // Show default location if permission denied
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

    /**
     * Formats an address for display using the new format: "street", "city", "state"
     * Same logic as PersonEditActivity.formatAddressForDisplay()
     */
    private fun formatAddressForDisplay(address: Address): Address {
        val parts = mutableListOf<String>()
        
        // Add street address (now contains complete street name + number)
        address.street?.let { parts.add(it) }
        
        // Add city (municipio), state (provincia) - no postal code or country needed
        address.city?.let { parts.add(it) } // City/Municipio
        address.state?.let { parts.add(it) } // State/Provincia
        
        val formattedAddress = parts.joinToString(", ")
        
        return address.copy(formattedAddress = formattedAddress)
    }
}
