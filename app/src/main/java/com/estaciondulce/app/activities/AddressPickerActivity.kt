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
import com.estaciondulce.app.models.Address
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
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import java.io.IOException
import java.util.Locale

/**
 * Activity for picking an address using Google Maps with Places API autocomplete.
 * Uses AutocompleteSupportFragment for better integration.
 */
class AddressPickerActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    // UI elements
    private lateinit var addressText: android.widget.TextView
    private lateinit var confirmButton: com.google.android.material.button.MaterialButton
    private lateinit var cancelButton: com.google.android.material.button.MaterialButton
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedMarker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private var geocoder: Geocoder? = null
    private lateinit var autocompleteFragment: AutocompleteSupportFragment
    private var currentAddress: android.location.Address? = null
    private var addressToEdit: Address? = null
    private var isEditMode = false

    companion object {
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_ADDRESS_LABEL = "address_label"
        const val EXTRA_EDIT_MODE = "edit_mode"
        const val EXTRA_ADDRESS_TO_EDIT = "address_to_edit"
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

        // Check if Google Maps API key is configured
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        android.util.Log.d("AddressPicker", "API Key: $apiKey")
        
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

        // Set up map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Check if we're in edit mode
        isEditMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
        if (isEditMode) {
            @Suppress("DEPRECATION")
            addressToEdit = intent.getParcelableExtra(EXTRA_ADDRESS_TO_EDIT)
            android.util.Log.d("AddressPicker", "Edit mode - Address to edit: $addressToEdit")
        }

        // Set up autocomplete fragment
        setupAutocompleteFragment()

        // Set up UI
        setupUI()
    }

    private fun setupAutocompleteFragment() {
        autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        // Set hint text in Spanish
        autocompleteFragment.setHint("Buscar dirección")

        // Specify the types of place data to return
        autocompleteFragment.setPlaceFields(listOf(
            Place.Field.ID, 
            Place.Field.NAME, 
            Place.Field.ADDRESS, 
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS_COMPONENTS
        ))

        // Set up a PlaceSelectionListener to handle the response
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                // Handle the selected place
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

            override fun onError(status: com.google.android.gms.common.api.Status) {
                android.util.Log.e("AddressPicker", "Error: ${status.statusMessage}")
                CustomToast.showError(this@AddressPickerActivity, "Error al buscar dirección: ${status.statusMessage}")
            }
        })

        // Add text change listener to detect coordinates
        setupCoordinateSearch()
    }

    private fun setupCoordinateSearch() {
        // Get the EditText from the AutocompleteSupportFragment
        val editText = autocompleteFragment.view?.findViewById<androidx.appcompat.widget.AppCompatEditText>(
            com.google.android.libraries.places.R.id.places_autocomplete_search_input
        )

        editText?.let { et ->
            // Enable paste functionality
            et.isLongClickable = true
            et.setTextIsSelectable(true)
            
            // Add text change listener
            et.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString()?.trim()
                    if (!text.isNullOrEmpty() && ::map.isInitialized) {
                        // Check if the text looks like coordinates (lat,lng format)
                        if (isCoordinateFormat(text)) {
                            handleCoordinateSearch(text)
                        }
                    }
                }
            })
            
            // Add paste listener for Ctrl+V
            et.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_V && event.isCtrlPressed) {
                    // Handle paste manually if needed
                    false // Let the system handle it
                } else {
                    false
                }
            }
        }
    }

    private fun isCoordinateFormat(text: String): Boolean {
        // Check for patterns like: -31.5375, -68.5364 or -31.5375,-68.5364 or -31.538599014282227,-68.5250015258789
        val coordinatePattern = Regex("^-?\\d+\\.\\d+\\s*,\\s*-?\\d+\\.\\d+$")
        return coordinatePattern.matches(text)
    }

    private fun handleCoordinateSearch(coordinateText: String) {
        try {
            android.util.Log.d("AddressPicker", "Processing coordinates: $coordinateText")
            val parts = coordinateText.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDouble()
                val lng = parts[1].trim().toDouble()
                
                android.util.Log.d("AddressPicker", "Parsed coordinates: lat=$lat, lng=$lng")
                
                // Validate coordinate ranges
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    val latLng = LatLng(lat, lng)
                    
                    // Update map
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    selectedLatLng = latLng
                    updateMarker(latLng)
                    
                    // Reverse geocode to get address
                    reverseGeocode(latLng)
                    
                    android.util.Log.d("AddressPicker", "Coordinate search successful: $lat, $lng")
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

            android.util.Log.d("AddressPicker", "Map ready")

            // Enable confirm button
            confirmButton.isEnabled = true
            confirmButton.text = "Confirmar"

            // Check if we're in edit mode and load existing address
            if (isEditMode && addressToEdit != null) {
                loadExistingAddress()
            } else {
                // Check location permission and get current location
                if (checkLocationPermission()) {
                    getCurrentLocation()
                } else {
                    // Default to San Juan Capital, Argentina if no permission
                    val defaultLocation = LatLng(-31.5375, -68.5364) // San Juan Capital coordinates
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AddressPicker", "Error in onMapReady: ${e.message}", e)
            CustomToast.showError(this, "Error al inicializar el mapa: ${e.message}")
        }
    }

    override fun onMapClick(latLng: LatLng) {
        android.util.Log.d("AddressPicker", "Map clicked at: $latLng")
        selectedLatLng = latLng
        updateMarker(latLng)
        
        // Update address text to show "Cargando..." while geocoding
        addressText.text = "Obteniendo dirección..."
        
        // Start reverse geocoding
        reverseGeocode(latLng)
    }

    private fun updateMarker(latLng: LatLng) {
        android.util.Log.d("AddressPicker", "Updating marker at: $latLng")
        selectedMarker?.remove()
        selectedMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Dirección seleccionada")
        )
        android.util.Log.d("AddressPicker", "Marker updated successfully")
    }

    private fun reverseGeocode(latLng: LatLng) {
        android.util.Log.d("AddressPicker", "Starting reverse geocoding for: $latLng")
        
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
                android.util.Log.d("AddressPicker", "Using modern geocoder API")
                geocoder!!.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    android.util.Log.d("AddressPicker", "Geocoder callback received ${addresses.size} addresses")
                    // Cancel timeout since we got a response
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    runOnUiThread {
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            currentAddress = address
                            val formattedAddress = buildFormattedAddress(address)
                            android.util.Log.d("AddressPicker", "Formatted address: $formattedAddress")
                            addressText.text = formattedAddress
                            android.util.Log.d("AddressPicker", "Address text updated in UI")
                        } else {
                            android.util.Log.w("AddressPicker", "No addresses found")
                            addressText.text = "No se pudo obtener la dirección"
                        }
                    }
                }
            } else {
                android.util.Log.d("AddressPicker", "Using legacy geocoder API")
                // Cancel timeout since we're using synchronous API
                timeoutHandler.removeCallbacks(timeoutRunnable)
                @Suppress("DEPRECATION")
                val addresses = geocoder!!.getFromLocation(latLng.latitude, latLng.longitude, 1)
                android.util.Log.d("AddressPicker", "Legacy geocoder returned ${addresses?.size ?: 0} addresses")
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    currentAddress = address
                    val formattedAddress = buildFormattedAddress(address)
                    android.util.Log.d("AddressPicker", "Formatted address: $formattedAddress")
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
        
        // Extract components from Place
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
            android.util.Log.d("AddressPicker", "Loading existing address: $address")
            
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
                address
            }

            // Always apply formatting to the final address before returning
            val formattedFinalAddress = formatAddressForDisplay(finalAddress)

            android.util.Log.d("AddressPicker", "Final formatted address: $formattedFinalAddress")

            // Return result
            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_ADDRESS, formattedFinalAddress)
            setResult(RESULT_OK, resultIntent)
            
            android.util.Log.d("AddressPicker", "Setting result and finishing")
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
                CustomToast.showError(this, "Permiso de ubicación denegado")
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
