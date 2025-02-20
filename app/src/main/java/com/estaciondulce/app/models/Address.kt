package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Address(
    override var id: String = "",
    val personId: String = "",
    val rawAddress: String = "",
    val formattedAddress: String = "",
    val placeId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val street: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null
) : Parcelable, Identifiable
