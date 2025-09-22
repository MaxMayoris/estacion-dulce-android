package com.estaciondulce.app.models.dtos

/**
 * DTO for Recipe data in Firestore (without Parcelable to avoid stability field)
 */
data class RecipeDTO(
    val name: String = "",
    val cost: Double = 0.0,
    val onSale: Boolean = false,
    val onSaleQuery: Boolean = false,
    val customizable: Boolean = false,
    val salePrice: Double = 0.0,
    val suggestedPrice: Double = 0.0,
    val profitPercentage: Double = 0.0,
    val unit: Int = 1,
    val images: List<String> = listOf(),
    val description: String = "",
    val detail: String = "",
    val categories: List<String> = listOf(),
    val sections: List<RecipeSectionDTO> = listOf(),
    val recipes: List<RecipeNestedDTO> = listOf()
)

/**
 * DTO for RecipeSection data in Firestore (without Parcelable to avoid stability field)
 */
data class RecipeSectionDTO(
    val id: String = "",
    val name: String = "",
    val products: List<RecipeProductDTO> = listOf()
)

/**
 * DTO for RecipeProduct data in Firestore (without Parcelable to avoid stability field)
 */
data class RecipeProductDTO(
    val productId: String = "",
    val quantity: Double = 0.0
)

/**
 * DTO for RecipeNested data in Firestore (without Parcelable to avoid stability field)
 */
data class RecipeNestedDTO(
    val recipeId: String = "",
    val quantity: Int = 0
)
