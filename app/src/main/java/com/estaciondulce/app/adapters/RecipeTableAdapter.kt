package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.parcelables.Recipe

class RecipeTableAdapter(
    recipeList: List<Recipe>,
    onRowClick: (Recipe) -> Unit,
    onViewClick: (Recipe) -> Unit
) : TableAdapter<Recipe>(recipeList, onRowClick, { }) {

    private val onViewClickCallback = onViewClick

    override fun getCellValues(item: Recipe, position: Int): List<Any> {
        return listOf(item.name)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Recipe, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
        
        // Show only view icon for recipes
        binding.viewIcon.visibility = android.view.View.VISIBLE
        binding.viewIcon.setOnClickListener { onViewClickCallback(item) }
        
        configureIconSpacing(binding)
    }
}
