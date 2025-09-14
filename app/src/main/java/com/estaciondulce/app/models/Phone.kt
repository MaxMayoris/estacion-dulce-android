package com.estaciondulce.app.models

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
