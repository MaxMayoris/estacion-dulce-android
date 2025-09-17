package com.estaciondulce.app.adapters

import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.EKitchenOrderStatus
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.TableColumnConfig

class KitchenOrderAdapter(
    movementList: List<Movement>,
    private val onRowClick: (Movement) -> Unit,
    private val attributeGetter: (Movement) -> List<Any>
) : RecyclerView.Adapter<KitchenOrderAdapter.KitchenOrderViewHolder>() {

    private var dataList: List<Movement> = movementList
    private var columnConfigs: List<TableColumnConfig> = emptyList()

    fun updateData(newData: List<Movement>) {
        dataList = newData
        notifyDataSetChanged()
    }

    fun setColumnConfigs(configs: List<TableColumnConfig>) {
        columnConfigs = configs
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KitchenOrderViewHolder {
        val binding = TableRowDynamicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return KitchenOrderViewHolder(binding, onRowClick)
    }

    override fun onBindViewHolder(holder: KitchenOrderViewHolder, position: Int) {
        holder.bind(dataList[position], position)
    }

    override fun getItemCount(): Int = dataList.size

    inner class KitchenOrderViewHolder(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (Movement) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movement: Movement, @Suppress("UNUSED_PARAMETER") position: Int) {
            val cellValues = attributeGetter(movement)
            bindRowContent(cellValues)
            
            // Hide all icons for kitchen orders - no actions needed
            binding.deleteIcon.visibility = android.view.View.GONE
            binding.actionIcon.visibility = android.view.View.GONE
            binding.mapsIcon.visibility = android.view.View.GONE
            
            // Set click listener
            binding.root.setOnClickListener {
                onRowClick(movement)
            }
        }

        private fun bindRowContent(cellValues: List<Any>) {
            binding.rowContainer.removeAllViews()
            for ((index, value) in cellValues.withIndex()) {
                val textView = TextView(binding.root.context).apply {
                    val isCurrency = index < columnConfigs.size && columnConfigs[index].isCurrency
                    text = when (value) {
                        is Double -> {
                            val formattedValue = String.format("%.2f", value)
                            if (isCurrency) "$$formattedValue" else formattedValue
                        }
                        is Boolean -> if (value) "Sí" else "No"
                        else -> value.toString()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setPadding(8, 6, 8, 6)
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    gravity = android.view.Gravity.CENTER
                    setTextColor(binding.root.context.getColor(R.color.table_cell_text))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.NORMAL)
                    
                    // Apply color to status column (index 2)
                    if (index == 2) {
                        setTextColor(getStatusColor(value.toString()))
                    }
                }
                binding.rowContainer.addView(textView)
            }
        }

        private fun getStatusColor(statusText: String): Int {
            return when {
                statusText.contains("Pendiente") -> Color.parseColor("#FF9800") // Orange
                statusText.contains("preparación") -> Color.parseColor("#2196F3") // Blue
                statusText.contains("Listo") -> Color.parseColor("#4CAF50") // Green
                statusText.contains("Cancelado") -> Color.parseColor("#F44336") // Red
                statusText.contains("Entregado") -> Color.parseColor("#9E9E9E") // Gray
                else -> binding.root.context.getColor(R.color.table_cell_text)
            }
        }
    }
}
