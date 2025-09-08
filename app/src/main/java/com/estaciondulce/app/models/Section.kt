package com.estaciondulce.app.models

/**
 * Section data model for organizing recipe ingredients and components.
 */
data class Section(
    override var id: String = "",
    val name: String = ""
) : Identifiable