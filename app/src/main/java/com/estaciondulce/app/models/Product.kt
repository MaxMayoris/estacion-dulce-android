package com.estaciondulce.app.models

data class Product(
    override var id: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val minimumQuantity: Double = 0.0,
    val cost: Double = 0.0,
    val measure: String = ""
) : Identifiable
