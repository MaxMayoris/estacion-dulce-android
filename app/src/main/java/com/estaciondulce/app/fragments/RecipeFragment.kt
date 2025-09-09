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
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.models.toColumnConfigs

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
            @Suppress("DEPRECATION")
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
        val columnConfigs = listOf("Nombre", "Costo", "Venta", "En Venta").toColumnConfigs(currencyColumns = setOf(1, 2))
        binding.recipeTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = RecipeAdapter(
                recipeList = sortedList,
                onRowClick = { recipe -> editRecipe(recipe) },
                onDeleteClick = { recipe -> deleteRecipe(recipe) }
            ) { recipe ->
                listOf(
                    recipe.name,
                    recipe.cost,
                    recipe.salePrice,
                    recipe.onSale
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    1 -> recipe.cost
                    2 -> recipe.salePrice
                    3 -> recipe.onSale
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
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EDIT_RECIPE_REQUEST_CODE)
    }

    /**
     * Deletes the specified recipe.
     */
    private fun deleteRecipe(recipe: Recipe) {
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = recipe.name,
            itemType = "receta",
            onConfirm = {
                recipesHelper.deleteRecipe(
                    recipeId = recipe.id,
                    onSuccess = {
                        CustomToast.showSuccess(requireContext(), "Receta '${recipe.name}' eliminada correctamente.")
                    },
                    onError = {
                        CustomToast.showError(requireContext(), "Error al eliminar la receta.")
                    }
                )
            }
        )
    }

    /**
     * Handles the result from RecipeEditActivity.
     */
    @Suppress("DEPRECATION")
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
