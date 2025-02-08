package com.estaciondulce.app.models

data class Category(
    override var id: String = "",
    val name: String = ""
) : Identifiable