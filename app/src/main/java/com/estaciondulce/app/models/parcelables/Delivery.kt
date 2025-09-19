package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Shipment details for delivery (only used when type = "shipment").
 */
@Parcelize
data class ShipmentDetails(
    val addressId: String = "",
    val formattedAddress: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val cost: Double = 0.0,
    val calculatedCost: Double = 0.0
) : Parcelable

/**
 * Delivery information for movements.
 */
@Parcelize
data class Delivery(
    val type: String = "", // "shipment" or "pickup"
    val date: Date = Date(),
    val status: String = "PENDING", // "PENDING", "DELIVERED", "CANCELED"
    val shipment: com.estaciondulce.app.models.parcelables.ShipmentDetails? = null // Only present when type = "shipment"
) : Parcelable
