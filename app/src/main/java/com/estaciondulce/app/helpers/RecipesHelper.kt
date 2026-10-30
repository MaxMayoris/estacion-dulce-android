package com.estaciondulce.app.helpers

import android.annotation.SuppressLint
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.dtos.RecipeDTO
import com.estaciondulce.app.models.mappers.toDTO
import com.estaciondulce.app.models.mappers.toMap
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Handles recipe CRUD operations and cost calculations.
 */
class RecipesHelper(
    private val genericHelper: GenericHelper = GenericHelper()
) {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun addRecipe(recipe: Recipe, onSuccess: (Recipe) -> Unit, onError: (Exception) -> Unit) {
        val recipeDTO = recipe.toDTO()
        val recipeData = recipeDTO.toMap()
               genericHelper.addDocument(
            collectionName = "recipes",
            data = recipeData,
            onSuccess = { documentId ->
                onSuccess(recipe.copy(id = documentId))
            },
            onError = onError
        )
    }

    fun updateRecipe(
        recipeId: String,
        recipe: Recipe,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val recipeDTO = recipe.toDTO()
        val recipeData = recipeDTO.toMap()
               genericHelper.updateDocument(
            collectionName = "recipes",
            documentId = recipeId,
            data = recipeData,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun deleteRecipe(recipeId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "recipes",
            documentId = recipeId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Marks the given recipes as out of stock (inStock = false) using batched writes
     * (Firestore allows at most 500 operations per batch).
     * @param recipes Recipes to mark as out of stock
     * @param onSuccess Called once every batch has been committed
     * @param onError Called with the first error that occurs
     */
    fun clearInStock(recipes: List<Recipe>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (recipes.isEmpty()) {
            onSuccess()
            return
        }
        val commits = recipes.chunked(MAX_BATCH_OPERATIONS).map { chunk ->
            val batch = db.batch()
            chunk.forEach { recipe ->
                batch.update(db.collection("recipes").document(recipe.id), "inStock", false)
            }
            batch.commit()
        }
        Tasks.whenAll(commits)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun isRecipeNameUnique(name: String, currentRecipeId: String?, onComplete: (Boolean) -> Unit) {
        val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        onComplete(recipes.none {
            it.name.equals(
                name,
                ignoreCase = true
            ) && it.id != currentRecipeId
        })
    }


    /**
     * Calculates cost per unit and suggested price for a recipe.
     * Suggested price is 60% markup over cost.
     */
    @SuppressLint("DefaultLocale")
    fun calculateCostAndSuggestedPrice(
        recipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: Map<String, Recipe>
    ): Pair<Double, Double> {
        var totalCost = 0.0
        for (nestedRecipe in recipe.recipes) {
            val recipeCost = allRecipes[nestedRecipe.recipeId]?.cost ?: 0.0
            totalCost += recipeCost * nestedRecipe.quantity
        }
        for (section in recipe.sections) {
            for (product in section.products) {
                val productCost = allProducts[product.productId]?.second ?: 0.0
                totalCost += productCost * product.quantity
            }
        }
        val units = if (recipe.unit > 0) recipe.unit.toDouble() else 1.0
        val costPerUnit = totalCost / units
        val suggestedPrice = costPerUnit * 1.6
        return Pair(
            String.format("%.2f", costPerUnit).toDouble(),
            String.format("%.2f", suggestedPrice).toDouble()
        )
    }

    companion object {
        private const val MAX_BATCH_OPERATIONS = 500
    }
}
