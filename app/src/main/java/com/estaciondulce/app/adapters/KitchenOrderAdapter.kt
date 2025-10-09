package com.estaciondulce.app.adapters

import android.graphics.Color
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.parcelables.Movement

class KitchenOrderAdapter(
    movementList: List<Movement>,
    onRowClick: (Movement) -> Unit,
    private val attributeGetter: (Movement) -> List<Any>
) : TableAdapter<Movement>(movementList, onRowClick, { /* No delete for kitchen orders */ }) {

    override fun getCellValues(item: Movement, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Movement, position: Int) {
        val cellValues = getCellValues(item, position)
        bindRowContentWithColors(binding, cellValues)
        
        binding.deleteIcon.visibility = android.view.View.GONE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        configureIconSpacing(binding)
    }

    private fun bindRowContentWithColors(binding: TableRowDynamicBinding, cellValues: List<Any>) {
        binding.rowContainer.removeAllViews()
        val context = binding.root.context
        for ((index, value) in cellValues.withIndex()) {
            val textView = TextView(context).apply {
                text = when (value) {
                    is Double -> String.format("%.2f", value)
                    is Boolean -> if (value) "Sí" else "No"
                    else -> value.toString()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(8, 6, 8, 6)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                setTextColor(context.getColor(R.color.table_cell_text))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.NORMAL)
                
                if (index == 2) {
                    setTextColor(getStatusColor(value.toString(), context))
                }
            }
            binding.rowContainer.addView(textView)
        }
    }

    private fun getStatusColor(statusText: String, context: android.content.Context): Int {
        return when {
            statusText.contains("Pendiente") -> Color.parseColor("#FF9800")
            statusText.contains("Listo para decorar") -> Color.parseColor("#2196F3")
            statusText.contains("Listo") -> Color.parseColor("#4CAF50")
            statusText.contains("Entregado") -> Color.parseColor("#9E9E9E")
            statusText.contains("Cancelado") -> Color.parseColor("#F44336")
            else -> context.getColor(R.color.table_cell_text)
        }
    }
}
