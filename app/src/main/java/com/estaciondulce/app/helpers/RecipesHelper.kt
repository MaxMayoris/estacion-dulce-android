package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Recipe
import com.google.firebase.firestore.FirebaseFirestore

class RecipesHelper(
    private val genericHelper: GenericHelper = GenericHelper(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun fetchRecipes(onSuccess: (List<Recipe>) -> Unit, onError: (Exception) -> Unit) {
        genericHelper.fetchCollectionWithToObject(
            collectionName = "recipes",
            clazz = Recipe::class.java,
            onSuccess = { recipes ->
                // Assign IDs manually if required, since Firestore's toObject() ignores them
                val recipesWithIds = recipes.map { it.copy(id = it.id) }
                onSuccess(recipesWithIds)
            },
            onError = onError
        )
    }

    fun addRecipe(recipe: Recipe, onSuccess: (Recipe) -> Unit, onError: (Exception) -> Unit) {
        val recipeData = mapOf(
            "name" to recipe.name,
            "cost" to recipe.cost,
            "suggestedPrice" to recipe.suggestedPrice,
            "salePrice" to recipe.salePrice,
            "onSale" to recipe.onSale,
            "categories" to recipe.categories,
            "sections" to recipe.sections
        )

        genericHelper.addDocument(
            collectionName = "recipes",
            data = recipeData,
            onSuccess = { documentId ->
                onSuccess(recipe.copy(id = documentId)) // Return the recipe with the new ID
            },
            onError = onError
        )
    }

    fun updateRecipe(recipeId: String, recipe: Recipe, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val recipeData = mapOf(
            "name" to recipe.name,
            "cost" to recipe.cost,
            "suggestedPrice" to recipe.suggestedPrice,
            "salePrice" to recipe.salePrice,
            "onSale" to recipe.onSale,
            "categories" to recipe.categories,
            "sections" to recipe.sections
        )

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

    fun isRecipeNameUnique(name: String, currentRecipeId: String?, onComplete: (Boolean) -> Unit) {
        db.collection("recipes")
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { documents ->
                val isUnique = documents.none { it.id != currentRecipeId }
                onComplete(isUnique)
            }
            .addOnFailureListener { e ->
                onComplete(false)
                e.printStackTrace()
            }
    }
}
