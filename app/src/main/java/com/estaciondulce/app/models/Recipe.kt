package com.estaciondulce.app.models

import RecipeSection
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class Recipe(
    var id: String = "",                         // Firestore document ID of the recipe
    val name: String = "",                       // Recipe name
    val cost: Double = 0.0,                      // Cost of creating the recipe
    val onSale: Boolean = false,                 // Whether the recipe is on sale
    val salePrice: Double = 0.0,                 // Current sale price
    val suggestedPrice: Double = 0.0,            // Suggested price
    val unit: Double = 0.0,                      // Unit measurement for the recipe
    val categories: List<String> = listOf(),     // List of category IDs from "categories" collection
    val sections: @RawValue List<RecipeSection> = listOf(), // List of sections for the recipe
    val recipes: @RawValue List<RecipeNested> = listOf() // List of nested recipes
) : Parcelable
