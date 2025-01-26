package com.estaciondulce.app.models

data class Product(
    val id: String = "", // Firestore document ID
    val name: String = "",
    val quantity: Int = 0,
    val minimumQuantity: Double = 0.0,
    val cost: Double = 0.0,
    val measure: String = ""
)
