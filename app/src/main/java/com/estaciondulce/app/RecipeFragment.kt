package com.estaciondulce.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.estaciondulce.app.adapters.RecipeAdapter
import com.estaciondulce.app.databinding.FragmentRecipeBinding
import com.estaciondulce.app.helpers.CategoriesHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.utils.CustomLoader

class RecipeFragment : Fragment() {

    private lateinit var loader: CustomLoader

    private var _binding: FragmentRecipeBinding? = null
    private val binding get() = _binding!!

    private val categoriesHelper = CategoriesHelper() // Helper for categories
    private val recipesHelper = RecipesHelper() // Helper for recipes

    private val recipeList = mutableListOf<Recipe>()
    private val categoriesMap = mutableMapOf<String, String>() // Map of category ID to name

    companion object {
        const val EDIT_RECIPE_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loader = CustomLoader(requireContext())

        loader.show()
        fetchData {
            setupTableView()
            loader.hide()
        }

        binding.addRecipeButton.setOnClickListener {
            val intent = Intent(requireContext(), RecipeEditActivity::class.java)
            startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE) // Launch RecipeEditActivity
        }
    }

    private fun fetchData(onComplete: () -> Unit) {
        var categoriesLoaded = false
        var recipesLoaded = false

        fun checkAllDataLoaded() {
            if (categoriesLoaded && recipesLoaded) {
                onComplete()
            }
        }

        // Fetch recipes
        recipesHelper.fetchRecipes(
            onSuccess = { recipes ->
                recipeList.clear()
                recipeList.addAll(recipes) // Use the full Recipe objects
                recipesLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeFragment", "Error fetching recipes: ${e.message}")
                recipesLoaded = true
                checkAllDataLoaded()
            }
        )

        // Fetch categories
        categoriesHelper.fetchCategories(
            onSuccess = {
                categoriesMap.putAll(it)
                categoriesLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeFragment", "Error fetching categories: ${e.message}")
                categoriesLoaded = true // Allow progress even if categories fail
                checkAllDataLoaded()
            }
        )
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

    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java)
        intent.putExtra("recipe", recipe) // Pass the recipe as Parcelable
        intent.putExtra("categoriesMap", HashMap(categoriesMap))
        val recipesMap = HashMap(recipeList.associateBy { it.id }) // Map recipe ID to Recipe object
        intent.putExtra("recipesMap", recipesMap)
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE) // Launch RecipeEditActivity
    }

    private fun deleteRecipe(recipe: Recipe) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar la receta '${recipe.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                recipesHelper.deleteRecipe(
                    recipeId = recipe.id,
                    onSuccess = {
                        recipeList.remove(recipe)
                        setupTableView() // Refresh the table
                        Toast.makeText(requireContext(), "Receta eliminada correctamente.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { e ->
                        Log.e("RecipeFragment", "Error deleting recipe: ${e.message}")
                        Toast.makeText(requireContext(), "Error al eliminar la receta.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
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
