package com.estaciondulce.app.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.TableColumnConfig

/**
 * Abstract base adapter for table-style RecyclerView with dynamic columns and alternating row colors.
 */
abstract class TableAdapter<T>(
    private var dataList: List<T>,
    private val onRowClick: (T) -> Unit,
    private val onDeleteClick: (T) -> Unit
) : RecyclerView.Adapter<TableAdapter.DynamicViewHolder<T>>() {

    private var columnConfigs: List<TableColumnConfig> = emptyList()

    fun updateData(newData: List<T>) {
        dataList = newData
        notifyDataSetChanged()
    }

    fun setColumnConfigs(configs: List<TableColumnConfig>) {
        columnConfigs = configs
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DynamicViewHolder<T> {
        val binding = TableRowDynamicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DynamicViewHolder(binding, onRowClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: DynamicViewHolder<T>, position: Int) {
        holder.bind(dataList[position], this::bindRow, position)
    }

    override fun getItemCount(): Int = dataList.size

    abstract fun getCellValues(item: T, position: Int): List<Any>

    protected fun bindRowContent(binding: TableRowDynamicBinding, cellValues: List<Any>) {
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
                maxLines = 2 // Permitir hasta 2 líneas
                ellipsize = TextUtils.TruncateAt.END
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            binding.rowContainer.addView(textView)
        }
    }

    abstract fun bindRow(binding: TableRowDynamicBinding, item: T, position: Int)
    
    /**
     * Helper method to configure icon spacing dynamically
     */
    protected fun configureIconSpacing(binding: TableRowDynamicBinding) {
        val visibleIcons = mutableListOf<android.widget.ImageView>()
        
        if (binding.deleteIcon.visibility == android.view.View.VISIBLE) {
            visibleIcons.add(binding.deleteIcon)
        }
        if (binding.actionIcon.visibility == android.view.View.VISIBLE) {
            visibleIcons.add(binding.actionIcon)
        }
        if (binding.mapsIcon.visibility == android.view.View.VISIBLE) {
            visibleIcons.add(binding.mapsIcon)
        }
        
        // Add margins between visible icons
        for (i in 1 until visibleIcons.size) {
            val layoutParams = visibleIcons[i].layoutParams as android.widget.LinearLayout.LayoutParams
            layoutParams.marginStart = 4
            visibleIcons[i].layoutParams = layoutParams
        }
    }

    class DynamicViewHolder<T>(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (T) -> Unit,
        private val onDeleteClick: (T) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T, bindRowCallback: (TableRowDynamicBinding, T, Int) -> Unit, position: Int) {
            // Reset all icons to gone initially
            binding.deleteIcon.visibility = android.view.View.GONE
            binding.actionIcon.visibility = android.view.View.GONE
            binding.mapsIcon.visibility = android.view.View.GONE
            
            // Remove margins from all icons
            val layoutParams = binding.actionIcon.layoutParams as android.widget.LinearLayout.LayoutParams
            layoutParams.marginStart = 0
            binding.actionIcon.layoutParams = layoutParams
            
            val mapsLayoutParams = binding.mapsIcon.layoutParams as android.widget.LinearLayout.LayoutParams
            mapsLayoutParams.marginStart = 0
            binding.mapsIcon.layoutParams = mapsLayoutParams
            bindRowCallback(binding, item, position)
            val isEvenRow = position % 2 == 0
            binding.root.background = binding.root.context.getDrawable(
                if (isEvenRow) R.drawable.table_row_even_background
                else R.drawable.table_row_background
            )
            binding.root.setOnClickListener { onRowClick(item) }
            binding.deleteIcon.setOnClickListener { onDeleteClick(item) }
        }
    }
}
