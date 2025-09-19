package com.estaciondulce.app.models.dtos

/**
 * DTO for ShipmentSettings data in Firestore (without Parcelable to avoid stability field)
 */
data class ShipmentSettingsDTO(
    val baseAddress: String = "",
    val fuelPrice: Double = 0.0,
    val litersPerKm: Double = 0.0
)
