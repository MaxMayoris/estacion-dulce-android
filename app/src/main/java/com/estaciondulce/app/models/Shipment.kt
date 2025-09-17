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
    val formattedAddress: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val calculatedCost: Double = 0.0,
    val cost: Double = 0.0,
    val date: Date? = null,
    val status: EShipmentStatus = EShipmentStatus.PENDING
) : Parcelable
