package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.parcelables.Person

/**
 * Helper class to intelligently select context data for Cha's responses
 * Optimizes token usage by including only relevant data
 */
object ContextSelector {

    private const val MAX_TOKENS = 3000
    private const val TOKENS_PER_PRODUCT = 8
    private const val TOKENS_PER_RECIPE = 15
    private const val TOKENS_PER_PERSON = 10
    private const val TOKENS_PER_MOVEMENT = 20

    /**
     * Selects relevant context data based on user query
     * @param userQuery The user's question
     * @param products All available products
     * @param recipes All available recipes
     * @param persons All available persons
     * @param movements All available movements
     * @return Formatted context string with relevant data
     */
    fun selectContext(
        userQuery: String,
        products: List<Product>?,
        recipes: List<Recipe>?,
        persons: List<Person>?,
        movements: List<Movement>?
    ): String {
        val context = StringBuilder()
        var remainingTokens = MAX_TOKENS

        // Always include products and recipes (they're essential)
        val selectedProducts = selectProducts(products, remainingTokens)
        val selectedRecipes = selectRecipes(recipes, remainingTokens)
        
        remainingTokens -= calculateTokens(selectedProducts, TOKENS_PER_PRODUCT)
        remainingTokens -= calculateTokens(selectedRecipes, TOKENS_PER_RECIPE)

        // Add products to context
        if (selectedProducts.isNotEmpty()) {
            context.append("PRODUCTOS:\n")
            selectedProducts.forEach { product ->
                context.append("- ${product.name}: ${product.quantity} ${product.measure}, $${product.salePrice}\n")
            }
            context.append("\n")
        }

        // Add recipes to context
        if (selectedRecipes.isNotEmpty()) {
            context.append("RECETAS:\n")
            selectedRecipes.forEach { recipe ->
                context.append("- ${recipe.name}: $${recipe.salePrice}\n")
            }
            context.append("\n")
        }

        // Conditionally include persons and movements based on keywords
        val shouldIncludePersons = shouldIncludePersons(userQuery)
        val shouldIncludeMovements = shouldIncludeMovements(userQuery)

        if (shouldIncludePersons && persons != null && remainingTokens > 0) {
            val selectedPersons = selectPersons(persons, remainingTokens)
            remainingTokens -= calculateTokens(selectedPersons, TOKENS_PER_PERSON)
            
            if (selectedPersons.isNotEmpty()) {
                context.append("PERSONAS:\n")
                selectedPersons.forEach { person ->
                    context.append("- ${person.name} ${person.lastName} (${person.type})\n")
                }
                context.append("\n")
            }
        }

        if (shouldIncludeMovements && movements != null && remainingTokens > 0) {
            val selectedMovements = selectMovements(movements, remainingTokens)
            remainingTokens -= calculateTokens(selectedMovements, TOKENS_PER_MOVEMENT)
            
            if (selectedMovements.isNotEmpty()) {
                context.append("MOVIMIENTOS RECIENTES:\n")
                selectedMovements.forEach { movement ->
                    context.append("- ${movement.type}: ${movement.detail} ($${movement.totalAmount})\n")
                }
                context.append("\n")
            }
        }

        return context.toString().ifEmpty { "No hay datos disponibles en este momento." }
    }

    /**
     * Selects products to include in context
     */
    private fun selectProducts(products: List<Product>?, maxTokens: Int): List<Product> {
        if (products.isNullOrEmpty()) return emptyList()
        
        val maxProducts = maxTokens / TOKENS_PER_PRODUCT
        return products.take(maxProducts)
    }

    /**
     * Selects recipes to include in context
     */
    private fun selectRecipes(recipes: List<Recipe>?, maxTokens: Int): List<Recipe> {
        if (recipes.isNullOrEmpty()) return emptyList()
        
        val maxRecipes = maxTokens / TOKENS_PER_RECIPE
        return recipes.take(maxRecipes)
    }

    /**
     * Selects persons to include in context
     */
    private fun selectPersons(persons: List<Person>, maxTokens: Int): List<Person> {
        val maxPersons = maxTokens / TOKENS_PER_PERSON
        return persons.take(maxPersons)
    }

    /**
     * Selects movements to include in context (recent ones first)
     */
    private fun selectMovements(movements: List<Movement>, maxTokens: Int): List<Movement> {
        val maxMovements = maxTokens / TOKENS_PER_MOVEMENT
        return movements.sortedByDescending { it.movementDate }.take(maxMovements)
    }

    /**
     * Determines if persons should be included based on user query
     */
    private fun shouldIncludePersons(userQuery: String): Boolean {
        val keywords = listOf("persona", "cliente", "proveedor", "contacto", "teléfono", "dirección")
        return keywords.any { keyword -> 
            userQuery.lowercase().contains(keyword.lowercase()) 
        }
    }

    /**
     * Determines if movements should be included based on user query
     */
    private fun shouldIncludeMovements(userQuery: String): Boolean {
        val keywords = listOf("compra", "venta", "movimiento", "pedido", "envío", "entrega", "pago", "dinero")
        return keywords.any { keyword -> 
            userQuery.lowercase().contains(keyword.lowercase()) 
        }
    }

    /**
     * Calculates estimated tokens for a list of items
     */
    private fun calculateTokens(items: List<*>, tokensPerItem: Int): Int {
        return items.size * tokensPerItem
    }
}
