package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Delivery
import com.estaciondulce.app.models.parcelables.ShipmentDetails
import com.estaciondulce.app.models.dtos.DeliveryDTO
import com.estaciondulce.app.models.dtos.ShipmentDetailsDTO

/**
 * Extension functions to convert between Delivery (Parcelable) and DeliveryDTO (Firestore)
 */

fun Delivery.toDTO(): DeliveryDTO {
    return DeliveryDTO(
        type = type,
        date = date,
        status = status,
        shipment = shipment?.toDTO()
    )
}

fun DeliveryDTO.toParcelable(): Delivery {
    return Delivery(
        type = type,
        date = date,
        status = status,
        shipment = shipment?.toParcelable()
    )
}

/**
 * Extension functions to convert between ShipmentDetails (Parcelable) and ShipmentDetailsDTO (Firestore)
 */

fun ShipmentDetails.toDTO(): ShipmentDetailsDTO {
    return ShipmentDetailsDTO(
        addressId = addressId,
        formattedAddress = formattedAddress,
        lat = lat,
        lng = lng,
        cost = cost,
        calculatedCost = calculatedCost
    )
}

fun ShipmentDetailsDTO.toParcelable(): ShipmentDetails {
    return ShipmentDetails(
        addressId = addressId,
        formattedAddress = formattedAddress,
        lat = lat,
        lng = lng,
        cost = cost,
        calculatedCost = calculatedCost
    )
}
