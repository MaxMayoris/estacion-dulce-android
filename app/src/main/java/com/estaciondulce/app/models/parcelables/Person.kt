package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Phone data model with prefix and suffix.
 */
@Parcelize
data class Phone(
    val phoneNumberPrefix: String = "",
    val phoneNumberSuffix: String = ""
) : Parcelable

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
) : Parcelable, com.estaciondulce.app.models.Identifiable

/**
 * Person data model with contact information and address references.
 */
@Parcelize
data class Person(
    override var id: String = "",
    val name: String = "",
    val lastName: String = "",
    val phones: List<Phone> = listOf(),
    val addresses: List<String> = listOf(),
    val type: String = ""
) : Parcelable, com.estaciondulce.app.models.Identifiable