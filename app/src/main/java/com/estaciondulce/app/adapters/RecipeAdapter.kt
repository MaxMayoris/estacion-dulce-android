package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.Recipe

class RecipeAdapter(
    recipeList: List<Recipe>,
    onRowClick: (Recipe) -> Unit,
    onDeleteClick: (Recipe) -> Unit,
    private val attributeGetter: (Recipe) -> List<Any>
) : TableAdapter<Recipe>(recipeList, onRowClick, onDeleteClick) {

    override fun getCellValues(item: Recipe, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Recipe, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
    }
}
