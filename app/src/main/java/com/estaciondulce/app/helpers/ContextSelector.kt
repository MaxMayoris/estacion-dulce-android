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

    private const val MAX_TOKENS = 5000
    private const val TOKENS_PER_PRODUCT = 15
    private const val TOKENS_PER_RECIPE = 80
    private const val TOKENS_PER_PERSON = 20
    private const val TOKENS_PER_MOVEMENT = 60

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
        movements: List<Movement>?,
        measures: List<com.estaciondulce.app.models.Measure>?
    ): String {
        val context = StringBuilder()
        var remainingTokens = MAX_TOKENS
        var totalTokensUsed = 0

        val selectedProducts = selectProducts(products, remainingTokens)
        val selectedRecipes = selectRecipes(recipes, remainingTokens)
        
        val productsTokens = calculateTokens(selectedProducts, TOKENS_PER_PRODUCT)
        val recipesTokens = calculateTokens(selectedRecipes, TOKENS_PER_RECIPE)
        remainingTokens -= productsTokens
        remainingTokens -= recipesTokens
        totalTokensUsed += productsTokens + recipesTokens
        

        if (selectedProducts.isNotEmpty()) {
            context.append("PRODUCTOS:\n")
            selectedProducts.forEach { product ->
                val measureName = getMeasureName(product.measure, measures)
                val stockStatus = if (product.quantity <= product.minimumQuantity) "BAJO" else "OK"
                context.append("${product.name};${product.quantity} $measureName;$${product.salePrice} (Stock:$stockStatus)\n")
            }
            context.append("\n")
        }

        if (selectedRecipes.isNotEmpty()) {
            context.append("RECETAS:\n")
            selectedRecipes.forEach { recipe ->
                val hasImages = if (recipe.images.isNotEmpty()) "Con imágenes" else "Sin imágenes"
                val saleStatus = if (recipe.onSale) "En venta" else "No disponible"
                val nestedRecipes = if (recipe.recipes.isNotEmpty()) {
                    recipe.recipes.joinToString(", ") { "x${it.quantity}" }
                } else ""
                
                context.append("${recipe.name};$${recipe.salePrice} (Costo:$${recipe.cost});$saleStatus;$hasImages\n")
                
                if (recipe.sections.isNotEmpty()) {
                    recipe.sections.forEach { section ->
                        context.append("${section.name}:\n")
                        section.products.forEach { product ->
                            val productName = getProductName(product.productId, products)
                            context.append("$productName;${product.quantity}\n")
                        }
                    }
                }
                
                if (nestedRecipes.isNotEmpty()) {
                    context.append("  Recetas incluidas: $nestedRecipes\n")
                }
            }
            context.append("\n")
        }

        val shouldIncludePersons = shouldIncludePersons(userQuery)
        val shouldIncludeMovements = shouldIncludeMovements(userQuery)

        if (shouldIncludePersons && persons != null && remainingTokens > 0) {
            val selectedPersons = selectPersons(persons, remainingTokens)
            val personsTokens = calculateTokens(selectedPersons, TOKENS_PER_PERSON)
            remainingTokens -= personsTokens
            totalTokensUsed += personsTokens
            
            
            if (selectedPersons.isNotEmpty()) {
                context.append("PERSONAS:\n")
                selectedPersons.forEach { person ->
                    val phoneInfo = if (person.phones.isNotEmpty()) {
                        val phone = person.phones.first()
                        "${phone.phoneNumberPrefix}${phone.phoneNumberSuffix}"
                    } else "Sin teléfono"
                    context.append("${person.name} ${person.lastName};${person.type};$phoneInfo\n")
                }
                context.append("\n")
            }
        }

        if (shouldIncludeMovements && movements != null && remainingTokens > 0) {
            val selectedMovements = selectMovements(movements, remainingTokens)
            val movementsTokens = calculateTokens(selectedMovements, TOKENS_PER_MOVEMENT)
            remainingTokens -= movementsTokens
            totalTokensUsed += movementsTokens
            
            
            if (selectedMovements.isNotEmpty()) {
                context.append("MOVIMIENTOS RECIENTES:\n")
                selectedMovements.forEach { movement ->
                    val personName = getPersonName(movement.personId, persons)
                    val deliveryInfo = movement.delivery?.let { delivery ->
                        val addressInfo = delivery.shipment?.formattedAddress ?: "Sin dirección"
                        " - Envío: ${delivery.status} ($addressInfo)"
                    } ?: ""
                    val kitchenStatus = movement.kitchenOrderStatus?.let { status ->
                        " - Cocina: ${status.name}"
                    } ?: ""
                    
                    context.append("${movement.type};${movement.detail};$personName;$${movement.totalAmount}$deliveryInfo$kitchenStatus\n")
                    
                    if (movement.items.isNotEmpty()) {
                        movement.items.forEach { item ->
                            val itemName = when (item.collection) {
                                "products" -> getProductName(item.collectionId, products)
                                "recipes" -> getRecipeName(item.collectionId, recipes)
                                "custom" -> item.customName ?: "Ítem personalizado"
                                else -> "Ítem desconocido"
                            }
                            val itemKitchenStatus = if (item.collection != "custom" || item.customName != "discount") {
                                " (Cocina: ${movement.kitchenOrderStatus?.name ?: "N/A"})"
                            } else ""
                            context.append("$itemName;${item.quantity}$itemKitchenStatus\n")
                        }
                    }
                }
                context.append("\n")
            }
        }

        val finalContext = context.toString().ifEmpty { "No hay datos disponibles en este momento." }
        
        
        return finalContext
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

    /**
     * Resolves measure ID to measure name
     */
    private fun getMeasureName(measureId: String, measures: List<com.estaciondulce.app.models.Measure>?): String {
        if (measures.isNullOrEmpty() || measureId.isEmpty()) return "unidades"
        return measures.find { it.id == measureId }?.name ?: "unidades"
    }

    /**
     * Resolves product ID to product name
     */
    private fun getProductName(productId: String, products: List<Product>?): String {
        if (products.isNullOrEmpty() || productId.isEmpty()) return "Producto desconocido"
        return products.find { it.id == productId }?.name ?: "Producto desconocido"
    }

    /**
     * Resolves person ID to person name
     */
    private fun getPersonName(personId: String, persons: List<Person>?): String {
        if (persons.isNullOrEmpty() || personId.isEmpty()) return "Persona desconocida"
        val person = persons.find { it.id == personId }
        return if (person != null) "${person.name} ${person.lastName}" else "Persona desconocida"
    }

    /**
     * Resolves recipe ID to recipe name
     */
    private fun getRecipeName(recipeId: String, recipes: List<Recipe>?): String {
        if (recipes.isNullOrEmpty() || recipeId.isEmpty()) return "Receta desconocida"
        return recipes.find { it.id == recipeId }?.name ?: "Receta desconocida"
    }
}
