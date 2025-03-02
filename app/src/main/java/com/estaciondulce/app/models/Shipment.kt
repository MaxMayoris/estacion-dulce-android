package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Shipment(
    val addressId: String = "",
    val shippingCost: Double = 0.0
) : Parcelable
