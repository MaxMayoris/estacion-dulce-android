package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class RecipesHelper(
    private val genericHelper: GenericHelper = GenericHelper(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

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

    fun isRecipeNameUnique(name: String, currentRecipeId: String?, onComplete: (Boolean) -> Unit) {
        val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        onComplete(recipes.none { it.name.equals(name, ignoreCase = true) && it.id != currentRecipeId })
    }

    fun updateCascadeCosts(
        updatedRecipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: MutableMap<String, Recipe>,
        visited: MutableSet<String> = mutableSetOf(),
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!visited.add(updatedRecipe.id)) {
            // Si ya se procesó esta receta, salimos para evitar un ciclo.
            onComplete()
            return
        }

        updateParentRecipesCosts(updatedRecipe, allProducts, allRecipes, {
            // Buscamos las recetas que usan la receta actualizada
            val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
            val immediateParents = recipes.filter { parent ->
                parent.recipes.any { nested -> nested.recipeId == updatedRecipe.id }
            }
            if (immediateParents.isEmpty()) {
                onComplete()
            } else {
                var pending = immediateParents.size
                immediateParents.forEach { parent ->
                    updateCascadeCosts(parent, allProducts, allRecipes, visited, {
                        pending--
                        if (pending == 0) onComplete()
                    }, onError)
                }
            }
        }, onError)
    }

    fun updateParentRecipesCosts(
        updatedRecipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: MutableMap<String, Recipe>, // Caché local mutable
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Actualizamos la caché local con la receta actualizada
        allRecipes[updatedRecipe.id] = updatedRecipe

        // Usamos el LiveData para obtener la lista de recetas sin consultar Firestore nuevamente.
        val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        val updateTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()

        for (parentRecipe in recipes) {
            // Si la receta padre utiliza la receta actualizada en sus recetas anidadas
            if (parentRecipe.recipes.any { it.recipeId == updatedRecipe.id }) {
                // Se recalcula el costo usando la caché actualizada
                val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(
                    parentRecipe, allProducts, allRecipes
                )
                val updateData = mapOf(
                    "cost" to newCost,
                    "suggestedPrice" to newSuggestedPrice
                )
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val updateTask = db.collection("recipes").document(parentRecipe.id).update(updateData)
                updateTasks.add(updateTask)
                // Actualizamos la caché local del padre
                allRecipes[parentRecipe.id] = parentRecipe.copy(cost = newCost, suggestedPrice = newSuggestedPrice)
            }
        }
        com.google.android.gms.tasks.Tasks.whenAll(updateTasks)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onError(it) }
    }


    /**
     * Updates the cost and suggested price for all recipes that use the specified product.
     *
     * It uses the helper function calculateCostAndSuggestedPrice from RecipesHelper.
     */
    fun updateAffectedRecipes(productId: String) {
        val db = FirebaseFirestore.getInstance()

        // Get all recipes that use the product.
        val affectedRecipes = getRecipesUsingProduct(productId)

        // Build a map of all products: product ID -> (name, cost)
        val allProducts: Map<String, Pair<String, Double>> =
            FirestoreRepository.productsLiveData.value
                ?.associate { it.id to Pair(it.name, it.cost) } ?: emptyMap()

        // Build a map of all recipes: recipe ID -> Recipe.
        val allRecipes: Map<String, Recipe> =
            FirestoreRepository.recipesLiveData.value
                ?.associateBy { it.id } ?: emptyMap()

        // For each affected recipe, calculate new cost and update the document.
        for (recipe in affectedRecipes) {
            val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(
                recipe, allProducts, allRecipes
            )
            db.collection("recipes").document(recipe.id)
                .update(mapOf("cost" to newCost, "suggestedPrice" to newSuggestedPrice))
        }
    }

    /**
     * Returns a list of recipes that use the specified product.
     *
     * @param productId The ID of the product.
     * @return A list of Recipe objects that reference the product.
     */
    private fun getRecipesUsingProduct(productId: String): List<Recipe> {
        // Get the global list of recipes from your repository.
        val allRecipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        return allRecipes.filter { recipe ->
            // Check if any section uses the product.
            recipe.sections.any { section ->
                section.products.any { it.productId == productId }
            } ||
                    // Check if any nested recipe uses the product.
                    recipe.recipes.any { it.recipeId == productId }
        }
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
