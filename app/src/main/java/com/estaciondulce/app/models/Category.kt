package com.estaciondulce.app.models

data class Category(
    override var id: String = "",
    val name: String = "",
    val isEvent: Boolean = false,
    val show: Boolean = true
) : Identifiable
