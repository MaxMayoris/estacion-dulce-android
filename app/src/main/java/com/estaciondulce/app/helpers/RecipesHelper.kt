package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
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
            "unit" to recipe.unit,
            "categories" to recipe.categories,
            "sections" to recipe.sections,
            "recipes" to recipe.recipes
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

    fun updateRecipe(
        recipeId: String,
        recipe: Recipe,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val recipeData = mapOf(
            "name" to recipe.name,
            "cost" to recipe.cost,
            "suggestedPrice" to recipe.suggestedPrice,
            "salePrice" to recipe.salePrice,
            "onSale" to recipe.onSale,
            "unit" to recipe.unit,
            "categories" to recipe.categories,
            "sections" to recipe.sections,
            "recipes" to recipe.recipes
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

    fun listenToRecipes(
        onUpdate: (List<Recipe>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("recipes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val recipes = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Recipe::class.java)?.copy(id = doc.id)
                    }
                    onUpdate(recipes)
                }
            }
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

    fun updateParentRecipesCosts(
        updatedRecipeId: String,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: MutableMap<String, Recipe>, // debe ser mutable para actualizar la caché local
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Fetch all recipes from Firestore
        db.collection("recipes")
            .get()
            .addOnSuccessListener { querySnapshot ->
                // List to hold update tasks
                val updateTasks = mutableListOf<Task<Void>>()
                for (document in querySnapshot.documents) {
                    // Convert document to a Recipe object and assign its id
                    val parentRecipe = document.toObject(Recipe::class.java)?.copy(id = document.id)
                    // Check if this parent recipe uses the updated recipe as nested
                    if (parentRecipe != null && parentRecipe.recipes.any { it.recipeId == updatedRecipeId }) {
                        // Recalculate cost using the helper function
                        val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(parentRecipe, allProducts, allRecipes)
                        val updateData = mapOf(
                            "cost" to newCost,
                            "suggestedPrice" to newSuggestedPrice
                        )
                        // Update Firestore document and add to list of tasks
                        val updateTask = db.collection("recipes").document(document.id).update(updateData)
                        updateTasks.add(updateTask)
                        // Optionally update the local cache:
                        allRecipes[parentRecipe.id] = parentRecipe.copy(cost = newCost, suggestedPrice = newSuggestedPrice)
                    }
                }
                // Wait for all update tasks to complete
                Tasks.whenAll(updateTasks)
                    .addOnSuccessListener { onComplete() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun calculateCostAndSuggestedPrice(
        recipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: Map<String, Recipe>
    ): Pair<Double, Double> {
        var totalCost = 0.0

        // Sumar costo de recetas anidadas
        for (nestedRecipe in recipe.recipes) {
            val recipeCost = allRecipes[nestedRecipe.recipeId]?.cost ?: 0.0
            totalCost += recipeCost * nestedRecipe.quantity
        }

        // Sumar costo de productos en secciones
        for (section in recipe.sections) {
            for (product in section.products) {
                val productCost = allProducts[product.productId]?.second ?: 0.0
                totalCost += productCost * product.quantity
            }
        }
        val units = if (recipe.unit > 0) recipe.unit else 1.0
        val costPerUnit = totalCost / units.toDouble()
        val suggestedPrice = costPerUnit * 1.6
        return Pair(costPerUnit, suggestedPrice)
    }
}
