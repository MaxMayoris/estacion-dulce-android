package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.MovementItem
import com.estaciondulce.app.models.dtos.MovementItemDTO

/**
 * Extension functions to convert between MovementItem (Parcelable) and MovementItemDTO (Firestore)
 */

fun MovementItem.toDTO(): MovementItemDTO {
    return MovementItemDTO(
        collection = collection,
        collectionId = collectionId,
        customName = customName,
        cost = cost,
        quantity = quantity
    )
}

fun MovementItemDTO.toParcelable(): MovementItem {
    return MovementItem(
        collection = collection,
        collectionId = collectionId,
        customName = customName,
        cost = cost,
        quantity = quantity
    )
}
