package com.estaciondulce.app.models

data class Measure(
    override var id: String = "",
    val name: String = "",
    val unit: String = ""
) : Identifiable
