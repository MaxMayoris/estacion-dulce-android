package com.estaciondulce.app.models.dtos

import java.util.Date

/**
 * DTO for Movement data in Firestore (without Parcelable to avoid stability field)
 */
data class MovementDTO(
    val type: String? = null, // EMovementType.name
    val personId: String = "",
    val movementDate: Date = Date(),
    val totalAmount: Double = 0.0,
    val items: List<MovementItemDTO> = listOf(),
    val delivery: DeliveryDTO? = null,
    val delta: Map<String, Double> = mapOf(),
    val appliedAt: Date? = null,
    val createdAt: Date? = null,
    val detail: String = "",
    val kitchenOrderStatus: String? = null // EKitchenOrderStatus.name
)
