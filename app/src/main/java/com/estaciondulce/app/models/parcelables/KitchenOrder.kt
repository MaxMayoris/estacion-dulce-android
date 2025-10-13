package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import com.estaciondulce.app.models.enums.EKitchenOrderItemStatus
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Kitchen order data model
 */
@Parcelize
data class KitchenOrder(
    override var id: String = "",
    val collection: String = "",
    val collectionId: String = "",
    val customName: String? = null,
    val name: String = "",
    val quantity: Double = 0.0,
    val status: EKitchenOrderItemStatus = EKitchenOrderItemStatus.PENDING,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) : Parcelable, com.estaciondulce.app.models.Identifiable
