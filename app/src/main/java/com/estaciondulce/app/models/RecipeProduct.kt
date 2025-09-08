package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Product reference with quantity for recipe ingredients.
 */
@Parcelize
data class RecipeProduct(
    val productId: String = "",
    var quantity: Double = 0.0
) : Parcelable

