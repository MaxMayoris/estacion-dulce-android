package com.estaciondulce.app.adapters

import android.view.View.TEXT_ALIGNMENT_CENTER
import android.widget.LinearLayout
import android.widget.TextView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.Recipe

class RecipeAdapter(
    recipeList: List<Recipe>,
    onRowClick: (Recipe) -> Unit,
    onDeleteClick: (Recipe) -> Unit,
    private val attributeGetter: (Recipe) -> List<Any> // Dynamically fetch column data
) : TableAdapter<Recipe>(recipeList, onRowClick, onDeleteClick) {

    override fun bindRow(binding: TableRowDynamicBinding, item: Recipe, position: Int) {
        // Apply alternating row colors
        val isEvenRow = position % 2 == 0
        binding.root.setBackgroundColor(
            if (isEvenRow) binding.root.context.getColor(R.color.purple_400)
            else binding.root.context.getColor(R.color.purple_200)
        )

        // Dynamically bind attributes
        val attributes = attributeGetter(item)
        binding.rowContainer.removeAllViews()
        for (attribute in attributes) {
            val textView = TextView(binding.root.context).apply {
                text = when (attribute) {
                    is Double -> "$${attribute}" // Format numbers
                    else -> attribute.toString()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(8, 8, 8, 8)
                textAlignment = TEXT_ALIGNMENT_CENTER
                setTextColor(binding.root.context.getColor(android.R.color.white))
            }
            binding.rowContainer.addView(textView)
        }
    }
}
