package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Address data model with geocoding information and structured location data.
 */
@Parcelize
data class Address(
    override var id: String = "",
    val label: String = "",
    val rawAddress: String = "",
    val formattedAddress: String = "",
    val placeId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val street: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val detail: String = ""
) : Parcelable, Identifiable
