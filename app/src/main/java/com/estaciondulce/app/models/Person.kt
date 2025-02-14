package com.estaciondulce.app.models

import PersonAddress
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Person(
    override var id: String = "",
    val name: String = "",
    val lastName: String = "",
    val phoneNumberPrefix: String = "",
    val phoneNumberSuffix: String = "",
    val addresses: @RawValue List<PersonAddress> = listOf(),
    val type: String = ""
) : Parcelable, Identifiable