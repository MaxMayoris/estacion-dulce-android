package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Shipment data model with delivery address, cost information and date.
 */
@Parcelize
data class Shipment(
    val addressId: String = "",
    val cost: Double = 0.0,
    val date: Date? = null
) : Parcelable
