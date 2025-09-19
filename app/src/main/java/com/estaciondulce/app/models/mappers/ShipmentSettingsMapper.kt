package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.ShipmentSettings
import com.estaciondulce.app.models.dtos.ShipmentSettingsDTO

/**
 * Extension functions to convert between ShipmentSettings (Parcelable) and ShipmentSettingsDTO (Firestore)
 */

fun ShipmentSettings.toDTO(): ShipmentSettingsDTO {
    return ShipmentSettingsDTO(
        baseAddress = baseAddress,
        fuelPrice = fuelPrice,
        litersPerKm = litersPerKm
    )
}

fun ShipmentSettingsDTO.toParcelable(): ShipmentSettings {
    return ShipmentSettings(
        baseAddress = baseAddress,
        fuelPrice = fuelPrice,
        litersPerKm = litersPerKm
    )
}
