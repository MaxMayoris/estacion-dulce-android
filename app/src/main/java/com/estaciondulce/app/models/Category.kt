package com.estaciondulce.app.models

import com.google.firebase.firestore.PropertyName

data class Category(
    override var id: String = "",
    val name: String = "",
    @get:PropertyName("isEvent")
    @set:PropertyName("isEvent")
    var isEvent: Boolean = false,
    val show: Boolean = true
) : Identifiable
