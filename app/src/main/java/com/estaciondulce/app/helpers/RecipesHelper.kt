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

    fun deleteRecipe(recipeId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("recipes")
            .document(recipeId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
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
