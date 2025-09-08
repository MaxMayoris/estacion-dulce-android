package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Nested recipe reference with quantity for composite recipes.
 */
@Parcelize
data class RecipeNested(
    val recipeId: String = "",
    var quantity: Int = 0
) : Parcelable
