package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Kitchen order data model
 */
@Parcelize
data class KitchenOrder(
    override var id: String = "",
    val productId: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val status: com.estaciondulce.app.models.enums.EKitchenOrderStatus = com.estaciondulce.app.models.enums.EKitchenOrderStatus.PENDING,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) : Parcelable, com.estaciondulce.app.models.Identifiable
