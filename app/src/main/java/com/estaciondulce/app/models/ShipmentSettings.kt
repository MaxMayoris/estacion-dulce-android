package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Shipment settings data model for configuration stored in Firestore.
 */
@Parcelize
data class ShipmentSettings(
    val baseAddress: String = "",
    val fuelPrice: Double = 0.0,
    val litersPerKm: Double = 0.0
) : Parcelable
