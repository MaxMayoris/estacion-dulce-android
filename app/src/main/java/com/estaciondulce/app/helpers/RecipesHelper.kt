package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

class RecipesHelper(
    private val genericHelper: GenericHelper = GenericHelper(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Adds a new recipe to the "recipes" collection.
     */
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
                onSuccess(recipe.copy(id = documentId))
            },
            onError = onError
        )
    }

    /**
     * Updates an existing recipe document.
     */
    fun updateRecipe(recipeId: String, recipe: Recipe, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
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

    /**
     * Deletes a recipe from the "recipes" collection.
     */
    fun deleteRecipe(recipeId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "recipes",
            documentId = recipeId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Checks if a recipe name is unique (ignoring case) using the global recipes LiveData.
     */
    fun isRecipeNameUnique(name: String, currentRecipeId: String?, onComplete: (Boolean) -> Unit) {
        val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        onComplete(recipes.none { it.name.equals(name, ignoreCase = true) && it.id != currentRecipeId })
    }

    /**
     * Recursively updates the costs and suggested prices of all parent recipes that use the updated recipe.
     * A visited set is used to avoid cycles.
     */
    fun updateCascadeCosts(
        updatedRecipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: MutableMap<String, Recipe>,
        visited: MutableSet<String> = mutableSetOf(),
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!visited.add(updatedRecipe.id)) {
            onComplete()
            return
        }
        updateParentRecipesCosts(updatedRecipe, allProducts, allRecipes, {
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

    /**
     * Updates the cost and suggested price for all parent recipes that use the updated recipe.
     * The local cache (allRecipes) is updated accordingly.
     */
    private fun updateParentRecipesCosts(
        updatedRecipe: Recipe,
        allProducts: Map<String, Pair<String, Double>>,
        allRecipes: MutableMap<String, Recipe>,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        allRecipes[updatedRecipe.id] = updatedRecipe
        val recipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        val updateTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
        for (parentRecipe in recipes) {
            if (parentRecipe.recipes.any { it.recipeId == updatedRecipe.id }) {
                val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(parentRecipe, allProducts, allRecipes)
                val updateData = mapOf("cost" to newCost, "suggestedPrice" to newSuggestedPrice)
                val updateTask = db.collection("recipes").document(parentRecipe.id).update(updateData)
                updateTasks.add(updateTask)
                allRecipes[parentRecipe.id] = parentRecipe.copy(cost = newCost, suggestedPrice = newSuggestedPrice)
            }
        }
        Tasks.whenAll(updateTasks)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Updates the cost and suggested price for all recipes that use the specified product.
     */
    fun updateAffectedRecipes(productId: String) {
        val db = FirebaseFirestore.getInstance()
        val affectedRecipes = getRecipesUsingProduct(productId)
        val allProducts: Map<String, Pair<String, Double>> =
            FirestoreRepository.productsLiveData.value?.associate { it.id to Pair(it.name, it.cost) } ?: emptyMap()
        val allRecipes: Map<String, Recipe> =
            FirestoreRepository.recipesLiveData.value?.associateBy { it.id } ?: emptyMap()
        for (recipe in affectedRecipes) {
            val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(recipe, allProducts, allRecipes)
            db.collection("recipes").document(recipe.id)
                .update(mapOf("cost" to newCost, "suggestedPrice" to newSuggestedPrice))
        }
    }

    /**
     * Returns a list of recipes that reference the specified product.
     */
    private fun getRecipesUsingProduct(productId: String): List<Recipe> {
        val allRecipes = FirestoreRepository.recipesLiveData.value ?: emptyList()
        return allRecipes.filter { recipe ->
            recipe.sections.any { section ->
                section.products.any { it.productId == productId }
            } || recipe.recipes.any { it.recipeId == productId }
        }
    }

    /**
     * Calculates the cost per unit and suggested price for a recipe.
     * Both values are rounded to 2 decimals.
     */
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

    fun updateCascadeAffectedRecipesFromProduct(productId: String, onComplete: () -> Unit, onError: (Exception) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        // Primero, actualizamos las recetas que usan directamente el producto.
        updateAffectedRecipes(productId)

        // Obtenemos el estado actual de las recetas y productos (asegúrate de que tus LiveData estén actualizadas)
        val allProducts: Map<String, Pair<String, Double>> =
            FirestoreRepository.productsLiveData.value?.associate { it.id to Pair(it.name, it.cost) } ?: emptyMap()
        val allRecipesList = FirestoreRepository.recipesLiveData.value ?: emptyList()
        val allRecipes: Map<String, Recipe> = allRecipesList.associateBy { it.id }

        // Función auxiliar: obtiene las recetas que usan una receta (anidada)
        fun getRecipesUsingRecipe(recipeId: String): List<Recipe> {
            return allRecipesList.filter { parent ->
                parent.recipes.any { nested -> nested.recipeId == recipeId }
            }
        }

        // Usamos un algoritmo BFS para recorrer la jerarquía de recetas afectadas.
        val processed = mutableSetOf<String>()
        val queue = ArrayDeque<Recipe>()

        // Comenzamos con las recetas directamente afectadas por el producto.
        val directAffected = allRecipesList.filter { recipe ->
            recipe.sections.any { section ->
                section.products.any { it.productId == productId }
            } || recipe.recipes.any { it.recipeId == productId }
        }
        queue.addAll(directAffected)

        fun processQueue() {
            if (queue.isEmpty()) {
                onComplete()
                return
            }
            val currentRecipe = queue.removeFirst()
            if (processed.contains(currentRecipe.id)) {
                processQueue()
                return
            }
            processed.add(currentRecipe.id)
            // Recalcular el costo y precio sugerido para currentRecipe.
            val (newCost, newSuggestedPrice) = calculateCostAndSuggestedPrice(currentRecipe, allProducts, allRecipes)
            db.collection("recipes").document(currentRecipe.id)
                .update(mapOf("cost" to newCost, "suggestedPrice" to newSuggestedPrice))
                .addOnSuccessListener {
                    // Luego, buscamos las recetas que usan currentRecipe y las agregamos a la cola.
                    val parentRecipes = getRecipesUsingRecipe(currentRecipe.id)
                    parentRecipes.forEach { parent ->
                        if (!processed.contains(parent.id)) {
                            queue.add(parent)
                        }
                    }
                    // Procesa el siguiente elemento.
                    processQueue()
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
        }

        processQueue()
    }
}
