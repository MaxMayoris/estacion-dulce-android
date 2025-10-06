package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.dtos.MovementDTO

/**
 * Extension functions to convert between Movement (Parcelable) and MovementDTO (Firestore)
 */

fun Movement.toDTO(): MovementDTO {
    return MovementDTO(
        type = type?.name,
        personId = personId,
        movementDate = movementDate,
        totalAmount = totalAmount,
        items = items.map { it.toDTO() },
        delivery = delivery?.toDTO(),
        delta = delta,
        appliedAt = appliedAt,
        createdAt = createdAt,
        detail = detail,
        kitchenOrderStatus = kitchenOrderStatus?.name,
        referenceImages = referenceImages,
        isStock = isStock
    )
}

fun MovementDTO.toParcelable(id: String = ""): Movement {
    return Movement(
        id = id,
        type = type?.let { EMovementType.valueOf(it) },
        personId = personId,
        movementDate = movementDate,
        totalAmount = totalAmount,
        items = items.map { it.toParcelable() },
        delivery = delivery?.toParcelable(),
        delta = delta,
        appliedAt = appliedAt,
        createdAt = createdAt,
        detail = detail,
        kitchenOrderStatus = kitchenOrderStatus?.let { EKitchenOrderStatus.valueOf(it) },
        referenceImages = referenceImages,
        isStock = isStock ?: false
    )
}

/**
 * Convert MovementDTO to Map for Firestore
 */
fun MovementDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "type" to type,
        "personId" to personId,
        "movementDate" to movementDate,
        "totalAmount" to totalAmount,
        "items" to items,
        "delivery" to delivery,
        "delta" to delta,
        "appliedAt" to appliedAt,
        "createdAt" to createdAt,
        "detail" to detail,
        "kitchenOrderStatus" to kitchenOrderStatus,
        "referenceImages" to referenceImages
    ).toMutableMap().apply {
        isStock?.let { put("isStock", it) }
    }
}
