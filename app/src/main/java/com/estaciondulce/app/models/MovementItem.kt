package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Movement item representing a product or recipe in a transaction.
 */
@Parcelize
data class MovementItem(
    val collection: String = "",
    val collectionId: String = "",
    var customName: String? = null,
    var cost: Double = 0.0,
    var quantity: Double = 0.0
) : Parcelable
