package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecipeProduct(
    val productId: String = "",
    val quantity: Double = 0.0
) : Parcelable

