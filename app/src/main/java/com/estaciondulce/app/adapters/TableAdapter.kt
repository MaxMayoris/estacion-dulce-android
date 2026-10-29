package com.estaciondulce.app.adapters

import com.estaciondulce.app.utils.FormatUtils
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

    /**
     * Number of action icons shown per row. Rows and header reserve the same fixed width for them
     * so the column titles stay centered over their values.
     */
    open val rowIconSlots: Int = 1

    protected fun columnWeight(index: Int): Float = columnConfigs.getOrNull(index)?.weight ?: 1f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DynamicViewHolder<T> {
        val binding = TableRowDynamicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        binding.iconsContainer.layoutParams = binding.iconsContainer.layoutParams.apply {
            width = iconColumnWidth(parent.context, rowIconSlots)
        }
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
                    is Double -> if (isCurrency) FormatUtils.formatCurrency(value) else FormatUtils.formatNumber(value)
                    is Boolean -> if (value) "Sí" else "No"
                    else -> value.toString()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, columnWeight(index))
                setPadding(8, 6, 8, 6)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                setTextColor(binding.root.context.getColor(R.color.table_cell_text))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textSize = 14f
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
        if (binding.viewIcon.visibility == android.view.View.VISIBLE) {
            visibleIcons.add(binding.viewIcon)
        }
        
        for (i in 1 until visibleIcons.size) {
            val layoutParams = visibleIcons[i].layoutParams as android.widget.LinearLayout.LayoutParams
            layoutParams.marginStart = 4
            visibleIcons[i].layoutParams = layoutParams
        }
    }

    companion object {
        /**
         * Fixed width in pixels of the row action-icon column for the given number of icons.
         */
        fun iconColumnWidth(context: android.content.Context, iconSlots: Int): Int {
            if (iconSlots <= 0) return 0
            val res = context.resources
            return iconSlots * res.getDimensionPixelSize(R.dimen.table_row_icon_size) +
                2 * res.getDimensionPixelSize(R.dimen.table_row_icons_padding)
        }
    }

    class DynamicViewHolder<T>(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (T) -> Unit,
        private val onDeleteClick: (T) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T, bindRowCallback: (TableRowDynamicBinding, T, Int) -> Unit, position: Int) {
            binding.deleteIcon.visibility = android.view.View.GONE
            binding.actionIcon.visibility = android.view.View.GONE
            binding.mapsIcon.visibility = android.view.View.GONE
            binding.viewIcon.visibility = android.view.View.GONE
            
            val layoutParams = binding.actionIcon.layoutParams as android.widget.LinearLayout.LayoutParams
            layoutParams.marginStart = 0
            binding.actionIcon.layoutParams = layoutParams
            
            val mapsLayoutParams = binding.mapsIcon.layoutParams as android.widget.LinearLayout.LayoutParams
            mapsLayoutParams.marginStart = 0
            binding.mapsIcon.layoutParams = mapsLayoutParams
            
            val viewLayoutParams = binding.viewIcon.layoutParams as android.widget.LinearLayout.LayoutParams
            viewLayoutParams.marginStart = 0
            binding.viewIcon.layoutParams = viewLayoutParams
            
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
