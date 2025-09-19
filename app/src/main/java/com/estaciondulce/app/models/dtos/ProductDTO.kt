package com.estaciondulce.app.models.dtos

/**
 * DTO for Product data in Firestore (without Parcelable to avoid stability field)
 */
data class ProductDTO(
    val name: String = "",
    val quantity: Double = 0.0,
    val minimumQuantity: Double = 0.0,
    val cost: Double = 0.0,
    val salePrice: Double = 0.0,
    val measure: String = ""
)
