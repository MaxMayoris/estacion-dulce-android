package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.DiscountEditActivity
import com.estaciondulce.app.adapters.RecipeAdapter
import com.estaciondulce.app.databinding.FragmentDiscountBinding
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.models.toColumnConfigs

/**
 * Fragment that displays the list of recipes that can have discounts.
 * It filters by onSale == true, and allows opening DiscountEditActivity.
 */
class DiscountFragment : Fragment() {

    private var _binding: FragmentDiscountBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository

    companion object {
        const val EDIT_DISCOUNT_REQUEST_CODE = 1002
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentDiscountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository.recipesLiveData.observe(viewLifecycleOwner) { recipes ->
            setupTableView(recipes)
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

    private fun setupTableView(recipes: List<Recipe>) {
        // Only get recipes that are onSale == true
        val onSaleRecipes = recipes.filter { it.onSale }
        
        val sortedList = onSaleRecipes.sortedBy { it.name }
        val columnConfigs = listOf("Nombre", "Venta", "Descuento").toColumnConfigs(currencyColumns = setOf(1))
        
        binding.discountTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = RecipeAdapter(
                recipeList = sortedList,
                onRowClick = { recipe -> editDiscount(recipe) },
                onDeleteClick = { _ -> /* No delete from this view */ }
            ) { recipe ->
                listOf(
                    recipe.name,
                    recipe.salePrice,
                    recipe.onDiscount
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    1 -> recipe.salePrice
                    2 -> recipe.onDiscount
                    else -> null
                }
            }
        )
    }

    private fun editDiscount(recipe: Recipe) {
        val intent = Intent(requireContext(), DiscountEditActivity::class.java).apply {
            putExtra("recipe", recipe)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EDIT_DISCOUNT_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_DISCOUNT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Updated automatically via Firestore listener
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
