package com.estaciondulce.app.models.dtos

import java.util.Date

/**
 * DTO for Delivery data in Firestore (without Parcelable to avoid stability field)
 */
data class DeliveryDTO(
    val type: String = "", // "SHIPMENT" or "PICKUP"
    val date: Date = Date(),
    val status: String = "PENDING", // "PENDING", "DELIVERED", "CANCELED"
    val shipment: ShipmentDetailsDTO? = null // Only present when type = "SHIPMENT"
)

/**
 * DTO for ShipmentDetails data in Firestore (without Parcelable to avoid stability field)
 */
data class ShipmentDetailsDTO(
    val addressId: String = "",
    val formattedAddress: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val cost: Double = 0.0,
    val calculatedCost: Double = 0.0
)
