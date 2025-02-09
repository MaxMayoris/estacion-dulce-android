package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.RecipeEditActivity
import com.estaciondulce.app.adapters.RecipeAdapter
import com.estaciondulce.app.databinding.FragmentRecipeBinding
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment that displays the list of recipes.
 */
class RecipeFragment : Fragment() {

    private var _binding: FragmentRecipeBinding? = null
    private val binding get() = _binding!!
    private val recipesHelper = RecipesHelper()
    private val repository = FirestoreRepository

    companion object {
        const val EDIT_RECIPE_REQUEST_CODE = 1001
    }

    /**
     * Inflates the fragment view.
     */
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Sets up LiveData observers and UI listeners.
     */
    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository.recipesLiveData.observe(viewLifecycleOwner) { recipes ->
            setupTableView(recipes)
        }
        repository.categoriesLiveData.observe(viewLifecycleOwner) {
            repository.recipesLiveData.value?.let { recipes ->
                setupTableView(recipes)
            }
        }
        binding.addRecipeButton.setOnClickListener {
            val intent = Intent(requireContext(), RecipeEditActivity::class.java)
            startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
        }
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
     * Configures the table view with the provided list of recipes.
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
     * Launches RecipeEditActivity to edit the selected recipe.
     */
    private fun editRecipe(recipe: Recipe) {
        val intent = Intent(requireContext(), RecipeEditActivity::class.java).apply {
            putExtra("recipe", recipe)
        }
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
    }

    /**
     * Deletes the specified recipe.
     */
    private fun deleteRecipe(recipe: Recipe) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar la receta '${recipe.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                recipesHelper.deleteRecipe(
                    recipeId = recipe.id,
                    onSuccess = {
                        Snackbar.make(binding.root, "Receta eliminada correctamente.", Snackbar.LENGTH_LONG).show()
                    },
                    onError = {
                        Snackbar.make(binding.root, "Error al eliminar la receta.", Snackbar.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()
    }

    /**
     * Handles the result from RecipeEditActivity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_RECIPE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Global LiveData updates automatically.
        }
    }

    /**
     * Cleans up the binding.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
