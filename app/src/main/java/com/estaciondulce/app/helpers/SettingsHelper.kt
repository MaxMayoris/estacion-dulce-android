package com.estaciondulce.app.helpers

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Helper for managing settings in Firestore.
 */
class SettingsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Updates the fuel price in settings/shipment/fuelPrice.
     */
    fun updateFuelPrice(
        fuelPrice: Double,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val updateData = mapOf(
            "fuelPrice" to fuelPrice
        )

        firestore.collection("settings")
            .document("shipment")
            .update(updateData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("SettingsHelper", "Error updating fuel price: ${exception.message}", exception)
                onError(exception)
            }
    }
}

