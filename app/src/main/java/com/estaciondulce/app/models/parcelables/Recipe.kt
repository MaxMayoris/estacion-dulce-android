package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

import java.util.Date

/**
 * Product reference with quantity for recipe ingredients.
 */
@Parcelize
data class RecipeProduct(
    val productId: String = "",
    var quantity: Double = 0.0
) : Parcelable

/**
 * Recipe section containing grouped ingredients with quantities.
 */
@Parcelize
data class RecipeSection(
    val id: String = "",
    val name: String = "",
    var products: @RawValue List<RecipeProduct> = listOf()
) : Parcelable

/**
 * Nested recipe reference with quantity for composite recipes.
 */
@Parcelize
data class RecipeNested(
    val recipeId: String = "",
    var quantity: Int = 0
) : Parcelable

/**
 * Details for baking process of a recipe.
 */
@Parcelize
data class BakingDetails(
    val temperature: Int = 0,
    val duration: Int = 0,
    val comments: String = ""
) : Parcelable

/**
 * Recipe data model with cost calculation, pricing, and hierarchical structure.
 */
@Parcelize
data class Recipe(
    override var id: String = "",
    val name: String = "",
    val cost: Double = 0.0,
    val onSale: Boolean = false,
    val onSaleQuery: Boolean = false,
    val onDiscount: Boolean = false,
    val customizable: Boolean = false,
    val inStock: Boolean = false,
    val salePrice: Double = 0.0,
    val discountPrice: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val profitPercentage: Double = 0.0,
    val unit: Int = 1,
    val images: List<String> = listOf(),
    val description: String = "",
    val detail: String = "",
    val categories: List<String> = listOf(),
    val sections: @RawValue List<RecipeSection> = listOf(),
    val recipes: @RawValue List<RecipeNested> = listOf(),
    val bakingDetails: BakingDetails? = null,
    val discountEndDate: Date? = null
) : Parcelable, com.estaciondulce.app.models.Identifiable {

    /**
     * Calculates the profit percentage based on cost and sale price.
     * Formula: ((salePrice - cost) / cost) × 100
     * Returns 0.0 if cost is 0 or invalid values.
     */
    fun calculateProfitPercentage(): Double {
        return if (cost > 0) {
            ((salePrice - cost) / cost) * 100
        } else {
            0.0
        }
    }

    /**
     * Checks if the discount is active and not expired.
     * @return true if discount is active and end date has not passed
     */
    fun isDiscountActive(): Boolean {
        if (!onDiscount) return false
        val limit = discountEndDate ?: return true
        return limit.after(Date())
    }
}
