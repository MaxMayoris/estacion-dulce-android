package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.Date

/**
 * Movement data model representing financial transactions with items and shipping.
 */
@Parcelize
data class Movement(
    override var id: String = "",
    val type: EMovementType? = null,
    val personId: String = "",
    val movementDate: Date = Date(),
    val totalAmount: Double = 0.0,
    val items: @RawValue List<MovementItem> = listOf(),
    val shipment: Shipment? = null,
    val delta: @RawValue Map<String, Double> = mapOf(),
    val appliedAt: Date? = null,
    val createdAt: Date? = null
) : Parcelable, Identifiable