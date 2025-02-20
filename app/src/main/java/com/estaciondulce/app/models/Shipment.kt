package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Shipment(
    override var id: String = "",
    val addressId: String = "",
    val shippingCost: Double = 0.0
) : Parcelable, Identifiable
