package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecipeNested(
    val recipeId: String = "",
    var quantity: Double = 0.0
) : Parcelable
