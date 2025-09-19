package com.estaciondulce.app.helpers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.estaciondulce.app.models.parcelables.ShipmentSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Helper class for managing shipment settings from Firestore.
 */
class ShipmentSettingsHelper {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val settingsCollection = firestore.collection("settings")
    private val shipmentSettingsDoc = settingsCollection.document("shipment")
    
    private val _shipmentSettings = MutableLiveData<ShipmentSettings>()
    val shipmentSettings: LiveData<ShipmentSettings> = _shipmentSettings
    
    private var listenerRegistration: ListenerRegistration? = null
    
    /**
     * Starts listening to shipment settings changes in Firestore.
     */
    fun startListening() {
        listenerRegistration = shipmentSettingsDoc.addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                android.util.Log.e("ShipmentSettingsHelper", "Error listening to shipment settings: ${exception.message}")
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists()) {
                try {
                    val settings = snapshot.toObject(ShipmentSettings::class.java)
                    settings?.let { nonNullSettings ->
                        _shipmentSettings.value = nonNullSettings
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShipmentSettingsHelper", "Error parsing shipment settings: ${e.message}")
                }
            } else {
                // Document doesn't exist - settings will be null until created in Firestore
                android.util.Log.w("ShipmentSettingsHelper", "Shipment settings document not found in Firestore")
            }
        }
    }
    
    /**
     * Stops listening to shipment settings changes.
     */
    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
    
    /**
     * Gets current shipment settings or null if not loaded.
     */
    fun getCurrentSettings(): ShipmentSettings? {
        return _shipmentSettings.value
    }
    
    /**
     * Checks if shipment settings are valid and loaded.
     */
    fun areSettingsValid(): Boolean {
        val settings = _shipmentSettings.value
        return settings != null && 
               settings.baseAddress.isNotEmpty() && 
               settings.fuelPrice > 0 && 
               settings.litersPerKm > 0
    }
}
