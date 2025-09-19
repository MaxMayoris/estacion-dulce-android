package com.estaciondulce.app.models.dtos

/**
 * DTO for MovementItem data in Firestore (without Parcelable to avoid stability field)
 */
data class MovementItemDTO(
    val collection: String = "",
    val collectionId: String = "",
    val customName: String? = null,
    val cost: Double = 0.0,
    val quantity: Double = 0.0
)
