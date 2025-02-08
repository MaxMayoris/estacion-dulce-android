package com.estaciondulce.app.models

data class Section(
    override var id: String = "",
    val name: String = ""
) : Identifiable