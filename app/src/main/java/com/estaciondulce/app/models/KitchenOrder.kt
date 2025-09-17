package com.estaciondulce.app.models

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
    val status: EKitchenOrderStatus = EKitchenOrderStatus.PENDING,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) : Parcelable, Identifiable
