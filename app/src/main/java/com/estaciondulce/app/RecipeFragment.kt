package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.RecipeEditActivity
import com.estaciondulce.app.adapters.RecipeAdapter
import com.estaciondulce.app.databinding.FragmentRecipeBinding
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar

class RecipeFragment : Fragment() {

    private var _binding: FragmentRecipeBinding? = null
    private val binding get() = _binding!!
    private val recipesHelper = RecipesHelper()

    // Global repository providing LiveData for recipes and categories.
    private val repository = FirestoreRepository

    companion object {
        const val EDIT_RECIPE_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the global recipes LiveData.
        repository.recipesLiveData.observe(viewLifecycleOwner, Observer { recipes ->
            setupTableView(recipes)
        })

        // Optionally observe categories if needed (for example, if categories affect the UI).
        repository.categoriesLiveData.observe(viewLifecycleOwner, Observer {
            // Refresh the table when categories change.
            repository.recipesLiveData.value?.let { recipes ->
                setupTableView(recipes)
            }
        })

        binding.addRecipeButton.setOnClickListener {
            val intent = Intent(requireContext(), RecipeEditActivity::class.java)
            startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
        }

        // Add a TextWatcher to the search bar to filter recipes.
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                val recipes = repository.recipesLiveData.value ?: emptyList()
                val filteredList = if (query.isEmpty()) recipes
                else recipes.filter { it.name.contains(query, ignoreCase = true) }
                setupTableView(filteredList)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Sets up the table view using the provided list of recipes.
     * The table shows "Nombre", "Costo", "Precio Venta" and "Rendimiento".
     */
    private fun setupTableView(recipes: List<Recipe>) {
        val sortedList = recipes.sortedBy { it.name }
        binding.recipeTable.setupTable(
            columnHeaders = listOf("Nombre", "Costo", "Precio Venta", "Rendimiento"),
            data = sortedList,
            adapter = RecipeAdapter(
                recipeList = sortedList,
                onRowClick = { recipe -> editRecipe(recipe) },
                onDeleteClick = { recipe -> deleteRecipe(recipe) }
            ) { recipe ->
                // Calculate "rendimiento": ((salePrice - cost)/cost)*100, formatted as percentage.
                val rendimiento = if (recipe.cost > 0)
                    ((recipe.salePrice - recipe.cost) / recipe.cost) * 100 else 0.0
                listOf(
                    recipe.name,
                    recipe.cost,
                    recipe.salePrice,
                    String.format("%.2f%%", rendimiento)
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

    /**
     * Launches RecipeEditActivity for editing a recipe.
     * Data for categories and recipes are available globally so no need to pass them via Intent.
     */
    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java).apply {
            putExtra("recipe", recipe)
        }
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
    }

    /**
     * Deletes a recipe using the repository's helper function.
     */
    private fun deleteRecipe(recipe: Recipe) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar la receta '${recipe.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                recipesHelper.deleteRecipe(
                    recipeId = recipe.id,
                    onSuccess = {
                        Snackbar.make(binding.root, "Receta eliminada correctamente.", Snackbar.LENGTH_LONG)
                            .show()
                    },
                    onError = { e ->
                        Snackbar.make(binding.root, "Error al eliminar la receta.", Snackbar.LENGTH_LONG)
                            .show()
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
            // Global LiveData will update automatically.
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
