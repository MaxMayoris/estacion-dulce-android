package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.*
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecipesHelperTest {

    private lateinit var recipesHelper: RecipesHelper
    private lateinit var genericHelper: GenericHelper

    @Before
    fun setup() {
        genericHelper = mockk(relaxed = true)
        recipesHelper = RecipesHelper(genericHelper)
    }

    @Test
    fun `calculateCostAndSuggestedPrice returns correct cost and suggested price based on products`() {
        // Arrange
        val product1 = RecipeProduct(productId = "prod1", quantity = 2.0)
        val product2 = RecipeProduct(productId = "prod2", quantity = 0.5)
        
        val section = RecipeSection(id = "sec1", name = "Dough", products = listOf(product1, product2))
        val recipe = Recipe(
            id = "rec1",
            name = "Cake",
            unit = 10,
            sections = listOf(section),
            recipes = emptyList() // No nested recipes for this test
        )

        // allProducts map format: Map<String, Pair<String, Double>> where Double is the cost
        val allProducts = mapOf(
            "prod1" to Pair("Flour", 10.0), // 2.0 quantity * 10.0 cost = 20.0
            "prod2" to Pair("Sugar", 20.0)  // 0.5 quantity * 20.0 cost = 10.0
        )
        // Total cost should be 30.0 for 10 units.
        // Cost per unit = 30.0 / 10 = 3.0.
        // Suggested price = 3.0 * 1.6 = 4.8.

        // Act
        val (costPerUnit, suggestedPrice) = recipesHelper.calculateCostAndSuggestedPrice(
            recipe,
            allProducts,
            emptyMap()
        )

        // Assert
        assertEquals(3.0, costPerUnit, 0.001)
        assertEquals(4.8, suggestedPrice, 0.001)
    }
    
    @Test
    fun `calculateCostAndSuggestedPrice returns correct cost with nested recipes`() {
        // Arrange
        val nestedRecipe = RecipeNested(recipeId = "baseRec", quantity = 2)
        val recipe = Recipe(
            id = "rec2",
            name = "Special Cake",
            unit = 1,
            sections = emptyList(),
            recipes = listOf(nestedRecipe)
        )

        val baseRecipeData = Recipe(id = "baseRec", name = "Base", cost = 15.0)
        val allRecipes = mapOf("baseRec" to baseRecipeData)
        
        // Total cost = 2 * 15.0 = 30.0 for 1 unit.
        // Cost per unit = 30.0
        // Suggested price = 30.0 * 1.6 = 48.0

        // Act
        val (costPerUnit, suggestedPrice) = recipesHelper.calculateCostAndSuggestedPrice(
            recipe,
            emptyMap(),
            allRecipes
        )

        // Assert
        assertEquals(30.0, costPerUnit, 0.001)
        assertEquals(48.0, suggestedPrice, 0.001)
    }

    @Test
    fun `calculateProfitPercentage returns correct value`() {
        val recipe = Recipe(
            cost = 50.0,
            salePrice = 75.0
        )
        assertEquals(50.0, recipe.calculateProfitPercentage(), 0.001)
    }

    @Test
    fun `isDiscountActive returns false if not on discount`() {
        val recipe = Recipe(onDiscount = false)
        assertEquals(false, recipe.isDiscountActive())
    }

    @Test
    fun `addRecipe calls genericHelper with correct collection and data`() {
        val recipe = Recipe(id = "", name = "New Recipe")
        val onSuccess: (Recipe) -> Unit = mockk(relaxed = true)
        val onError: (Exception) -> Unit = mockk(relaxed = true)

        recipesHelper.addRecipe(recipe, onSuccess, onError)

        val collectionSlot = slot<String>()
        val dataSlot = slot<Map<String, Any?>>()
        verify {
            genericHelper.addDocument(
                collectionName = capture(collectionSlot),
                data = capture(dataSlot),
                onSuccess = any(),
                onError = any()
            )
        }
        assertEquals("recipes", collectionSlot.captured)
        assertEquals("New Recipe", dataSlot.captured["name"])
    }
}
