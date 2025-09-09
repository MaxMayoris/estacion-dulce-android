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
    val onSaleQuery: Boolean = false,
    val salePrice: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val profitPercentage: Double = 0.0,
    val unit: Int = 1,
    val images: List<String> = listOf(),
    val description: String = "",
    val categories: List<String> = listOf(),
    val sections: @RawValue List<RecipeSection> = listOf(),
    val recipes: @RawValue List<RecipeNested> = listOf()
) : Parcelable, Identifiable {

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
}
