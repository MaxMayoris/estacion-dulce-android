package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.parcelables.RecipeSection
import com.estaciondulce.app.models.parcelables.RecipeProduct
import com.estaciondulce.app.models.parcelables.RecipeNested
import com.estaciondulce.app.models.dtos.RecipeDTO
import com.estaciondulce.app.models.dtos.RecipeSectionDTO
import com.estaciondulce.app.models.dtos.RecipeProductDTO
import com.estaciondulce.app.models.dtos.RecipeNestedDTO

/**
 * Extension functions to convert between Recipe (Parcelable) and RecipeDTO (Firestore)
 */

fun Recipe.toDTO(): RecipeDTO {
    return RecipeDTO(
        name = name,
        cost = cost,
        onSale = onSale,
        onSaleQuery = onSaleQuery,
        customizable = customizable,
        inStock = inStock,
        salePrice = salePrice,
        suggestedPrice = suggestedPrice,
        profitPercentage = profitPercentage,
        unit = unit,
        images = images,
        description = description,
        detail = detail,
        categories = categories,
        sections = sections.map { it.toDTO() },
        recipes = recipes.map { it.toDTO() }
    )
}

fun RecipeDTO.toParcelable(id: String = ""): Recipe {
    return Recipe(
        id = id,
        name = name,
        cost = cost,
        onSale = onSale,
        onSaleQuery = onSaleQuery,
        customizable = customizable,
        inStock = inStock,
        salePrice = salePrice,
        suggestedPrice = suggestedPrice,
        profitPercentage = profitPercentage,
        unit = unit,
        images = images,
        description = description,
        detail = detail,
        categories = categories,
        sections = sections.map { it.toParcelable() },
        recipes = recipes.map { it.toParcelable() }
    )
}

/**
 * Extension functions to convert between RecipeSection (Parcelable) and RecipeSectionDTO (Firestore)
 */

fun RecipeSection.toDTO(): RecipeSectionDTO {
    return RecipeSectionDTO(
        id = id,
        name = name,
        products = products.map { it.toDTO() }
    )
}

fun RecipeSectionDTO.toParcelable(): RecipeSection {
    return RecipeSection(
        id = id,
        name = name,
        products = products.map { it.toParcelable() }
    )
}

/**
 * Extension functions to convert between RecipeProduct (Parcelable) and RecipeProductDTO (Firestore)
 */

fun RecipeProduct.toDTO(): RecipeProductDTO {
    return RecipeProductDTO(
        productId = productId,
        quantity = quantity
    )
}

fun RecipeProductDTO.toParcelable(): RecipeProduct {
    return RecipeProduct(
        productId = productId,
        quantity = quantity
    )
}

/**
 * Extension functions to convert between RecipeNested (Parcelable) and RecipeNestedDTO (Firestore)
 */

fun RecipeNested.toDTO(): RecipeNestedDTO {
    return RecipeNestedDTO(
        recipeId = recipeId,
        quantity = quantity
    )
}

fun RecipeNestedDTO.toParcelable(): RecipeNested {
    return RecipeNested(
        recipeId = recipeId,
        quantity = quantity
    )
}

/**
 * Convert RecipeDTO to Map for Firestore
 */
fun RecipeDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "cost" to cost,
        "onSale" to onSale,
        "onSaleQuery" to onSaleQuery,
        "customizable" to customizable,
        "inStock" to inStock,
        "salePrice" to salePrice,
        "suggestedPrice" to suggestedPrice,
        "profitPercentage" to profitPercentage,
        "unit" to unit,
        "images" to images,
        "description" to description,
        "detail" to detail,
        "categories" to categories,
        "sections" to sections,
        "recipes" to recipes
    )
}
