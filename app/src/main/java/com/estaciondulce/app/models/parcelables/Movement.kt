package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.Date

/**
 * Movement data model representing financial transactions with items and delivery.
 */
@Parcelize
data class Movement(
    override var id: String = "",
    val type: com.estaciondulce.app.models.enums.EMovementType? = null,
    val personId: String = "",
    val movementDate: Date = Date(),
    val totalAmount: Double = 0.0,
    val items: @RawValue List<com.estaciondulce.app.models.parcelables.MovementItem> = listOf(),
    val delivery: com.estaciondulce.app.models.parcelables.Delivery? = null,
    val delta: @RawValue Map<String, Double> = mapOf(),
    val appliedAt: Date? = null,
    val createdAt: Date? = null,
    val detail: String = "",
    val kitchenOrderStatus: com.estaciondulce.app.models.enums.EKitchenOrderStatus? = null
) : Parcelable, com.estaciondulce.app.models.Identifiable
