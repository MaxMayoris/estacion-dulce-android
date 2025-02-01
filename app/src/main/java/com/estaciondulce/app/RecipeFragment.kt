package com.estaciondulce.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    // Helpers for categories and recipes
    private val categoriesHelper = CategoriesHelper()
    private val recipesHelper = RecipesHelper()

    private val recipeList = mutableListOf<Recipe>()
    private val categoriesMap = mutableMapOf<String, String>() // Map of category ID to name

    companion object {
        const val EDIT_RECIPE_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loader = CustomLoader(requireContext())

        // Listen to real-time updates for recipes
        recipesHelper.listenToRecipes(
            onUpdate = { recipes ->
                recipeList.clear()
                recipeList.addAll(recipes)
                // Call setupTableView with full list initially
                setupTableView(recipeList)
            },
            onError = { error ->
                Log.e("RecipeFragment", "Listen failed.", error)
            }
        )

        loader.show()
        fetchData {
            setupTableView(recipeList)
            loader.hide()
        }

        binding.addRecipeButton.setOnClickListener {
            val intent = Intent(requireContext(), RecipeEditActivity::class.java)
            startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
        }

        // Add a TextWatcher to the search bar to filter recipes
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                val filteredList = if (query.isEmpty()) {
                    recipeList
                } else {
                    recipeList.filter { it.name.contains(query, ignoreCase = true) }
                }
                setupTableView(filteredList)
            }
            override fun afterTextChanged(s: Editable?) { }
        })
    }

    private fun fetchData(onComplete: () -> Unit) {
        var categoriesLoaded = false
        var recipesLoaded = false

        fun checkAllDataLoaded() {
            if (categoriesLoaded && recipesLoaded) onComplete()
        }

        recipesHelper.fetchRecipes(
            onSuccess = { recipes ->
                recipeList.clear()
                recipeList.addAll(recipes)
                recipesLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeFragment", "Error fetching recipes: ${e.message}")
                recipesLoaded = true
                checkAllDataLoaded()
            }
        )

        categoriesHelper.fetchCategories(
            onSuccess = {
                categoriesMap.putAll(it)
                categoriesLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeFragment", "Error fetching categories: ${e.message}")
                categoriesLoaded = true
                checkAllDataLoaded()
            }
        )
    }

    // Modified setupTableView accepts an optional list parameter.
    private fun setupTableView(list: List<Recipe> = recipeList) {
        val sortedList = list.sortedBy { it.name }
        binding.recipeTable.setupTable(
            columnHeaders = listOf("Nombre", "Costo", "Precio Venta", "Rendimiento"),
            data = sortedList,
            adapter = RecipeAdapter(
                recipeList = sortedList,
                onRowClick = { recipe -> editRecipe(recipe) },
                onDeleteClick = { recipe -> deleteRecipe(recipe) }
            ) { recipe ->
                val rendimiento = if (recipe.cost > 0)
                    ((recipe.salePrice - recipe.cost) / recipe.cost) * 100 else 0.0
                listOf(
                    recipe.name,               // Nombre
                    recipe.cost,               // Costo
                    recipe.salePrice,          // Precio Venta
                    String.format("%.1f%%", rendimiento)  // Rendimiento
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    1 -> recipe.cost
                    2 -> recipe.salePrice
                    3 -> {
                        val rendimiento = if (recipe.cost > 0)
                            ((recipe.salePrice - recipe.cost) / recipe.cost) * 100 else 0.0
                        String.format("%.1f%%", rendimiento)
                    }
                    else -> null
                }
            }
        )
    }

    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java).apply {
            putExtra("recipe", recipe) // Pass the recipe as Parcelable
            putExtra("categoriesMap", HashMap(categoriesMap))
            putExtra("recipesMap", HashMap(recipeList.associateBy { it.id }))
        }
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
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
                        setupTableView(recipeList)
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
            val index = recipeList.indexOfFirst { it.id == updatedRecipe.id }
            if (index != -1) {
                recipeList[index] = updatedRecipe // Update the existing recipe
            } else {
                recipeList.add(updatedRecipe) // Add the new recipe
            }
            setupTableView(recipeList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
