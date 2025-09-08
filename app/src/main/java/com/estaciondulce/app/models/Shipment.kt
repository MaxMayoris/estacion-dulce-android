package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Shipment data model with delivery address and cost information.
 */
@Parcelize
data class Shipment(
    val addressId: String = "",
    val shippingCost: Double = 0.0
) : Parcelable
