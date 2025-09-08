package com.estaciondulce.app.models

import RecipeSection
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Recipe data model with cost calculation, pricing, and hierarchical structure.
 */
@Parcelize
data class Recipe(
    override var id: String = "",
    val name: String = "",
    val cost: Double = 0.0,
    val onSale: Boolean = false,
    val salePrice: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val unit: Int = 1,
    val image: String = "",
    val description: String = "",
    val categories: List<String> = listOf(),
    val sections: @RawValue List<RecipeSection> = listOf(),
    val recipes: @RawValue List<RecipeNested> = listOf()
) : Parcelable, Identifiable
