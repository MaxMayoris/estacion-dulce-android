package com.estaciondulce.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.estaciondulce.app.adapters.RecipeAdapter
import com.estaciondulce.app.databinding.FragmentRecipeBinding
import com.estaciondulce.app.models.Recipe
import com.google.firebase.firestore.FirebaseFirestore


class RecipeFragment : Fragment() {

    private var _binding: FragmentRecipeBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private val recipeList = mutableListOf<Recipe>()
    private val categoriesMap = mutableMapOf<String, String>() // Map of category ID to name

    companion object {
        const val EDIT_RECIPE_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("RecipeFragment", "onCreateView called")
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("RecipeFragment", "onViewCreated called")

        fetchCategories { fetchRecipes() } // Fetch categories, then fetch recipes

        binding.addRecipeButton.setOnClickListener {
            val intent = Intent(requireContext(), RecipeEditActivity::class.java)
            startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE) // Launch RecipeEditActivity
        }
    }

    private fun fetchCategories(onComplete: () -> Unit) {
        db.collection("categories")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val categoryName = document.getString("name") ?: "Unknown"
                    categoriesMap[document.id] = categoryName
                }
                onComplete() // Continue fetching recipes after categories
            }
            .addOnFailureListener { e ->
                Log.e("RecipeFragment", "Error fetching categories: ${e.message}")
                onComplete()
            }
    }

    private fun fetchRecipes() {
        db.collection("recipes")
            .get()
            .addOnSuccessListener { documents ->
                recipeList.clear()
                for (document in documents) {
                    val recipe = document.toObject(Recipe::class.java).copy(id = document.id)
                    recipeList.add(recipe)
                }
                setupTableView()
            }
            .addOnFailureListener { e ->
                Log.e("RecipeFragment", "Error fetching recipes: ${e.message}")
            }
    }

    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java)
        intent.putExtra("recipe", recipe) // Pass the recipe as Parcelable
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE) // Launch RecipeEditActivity
    }

    private fun setupTableView() {
        val sortedList = recipeList.sortedBy { it.name }
        binding.recipeTable.setupTable(
            columnHeaders = listOf("Nombre", "Costo", "Precio Venta", "Categorías"),
            data = sortedList,
            adapter = RecipeAdapter(
                recipeList = sortedList,
                onRowClick = { recipe -> editRecipe(recipe) },
                onDeleteClick = { recipe -> deleteRecipe(recipe) }
            ) { recipe ->
                listOf(
                    recipe.name,              // Nombre
                    recipe.cost,              // Costo
                    recipe.salePrice,         // Precio Venta
                    recipe.categories.joinToString(", ") { categoriesMap[it] ?: "Unknown" } // Categorías
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    1 -> recipe.cost
                    2 -> recipe.salePrice
                    3 -> recipe.categories.joinToString(", ") { categoriesMap[it] ?: "Unknown" }
                    else -> null
                }
            }
        )
    }

    private fun filterRecipes(query: String) {
        val filteredList = recipeList.filter {
            it.name.contains(query, ignoreCase = true)
        }
        binding.recipeTable.setupTable(
            columnHeaders = listOf("Nombre", "Costo", "Precio Venta", "Categorías"),
            data = filteredList,
            adapter = RecipeAdapter(
                recipeList = filteredList,
                onRowClick = { recipe -> editRecipe(recipe) },
                onDeleteClick = { recipe -> deleteRecipe(recipe) }
            ) { recipe ->
                listOf(
                    recipe.name,              // Nombre
                    recipe.cost,              // Costo
                    recipe.salePrice,         // Precio Venta
                    recipe.categories.joinToString(", ") { categoriesMap[it] ?: "Unknown" } // Categorías
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    1 -> recipe.cost
                    2 -> recipe.salePrice
                    3 -> recipe.categories.joinToString(", ") { categoriesMap[it] ?: "Unknown" }
                    else -> null
                }
            }
        )
    }

    private fun deleteRecipe(recipe: Recipe) {
        db.collection("recipes")
            .document(recipe.id)
            .delete()
            .addOnSuccessListener {
                recipeList.remove(recipe)
                setupTableView() // Refresh the table
            }
            .addOnFailureListener { e ->
                Log.e("RecipeFragment", "Error deleting recipe: ${e.message}")
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_RECIPE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val updatedRecipe = data?.getParcelableExtra<Recipe>("updatedRecipe") ?: return

            // Update the recipe in the list
            val index = recipeList.indexOfFirst { it.id == updatedRecipe.id }
            if (index != -1) {
                recipeList[index] = updatedRecipe // Update the existing recipe
            } else {
                recipeList.add(updatedRecipe) // Add the new recipe
            }

            // Refresh the table view
            setupTableView()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
