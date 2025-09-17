package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Product data model with inventory tracking and cost management.
 */
@Parcelize
data class Product(
    override var id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val minimumQuantity: Double = 0.0,
    val cost: Double = 0.0,
    val salePrice: Double = 0.0,
    val measure: String = ""
) : Parcelable, Identifiable
