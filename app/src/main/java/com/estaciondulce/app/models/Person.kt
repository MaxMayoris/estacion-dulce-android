package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Person data model with contact information and address references.
 */
@Parcelize
data class Person(
    override var id: String = "",
    val name: String = "",
    val lastName: String = "",
    val phoneNumberPrefix: String = "",
    val phoneNumberSuffix: String = "",
    val addresses: List<String> = listOf(),
    val type: String = ""
) : Parcelable, Identifiable