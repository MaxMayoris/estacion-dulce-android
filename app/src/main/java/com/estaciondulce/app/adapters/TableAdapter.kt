package com.estaciondulce.app.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding

/**
 * Abstract base adapter for table-style RecyclerView with dynamic columns and alternating row colors.
 */
abstract class TableAdapter<T>(
    private var dataList: List<T>,
    private val onRowClick: (T) -> Unit,
    private val onDeleteClick: (T) -> Unit
) : RecyclerView.Adapter<TableAdapter.DynamicViewHolder<T>>() {

    fun updateData(newData: List<T>) {
        dataList = newData
        notifyDataSetChanged()
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
        for (value in cellValues) {
            val textView = TextView(binding.root.context).apply {
                text = when (value) {
                    is Double -> String.format("%.2f", value)
                    else -> value.toString()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(12, 12, 12, 12)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                setTextColor(binding.root.context.getColor(R.color.table_cell_text))
                maxLines = 2 // Permitir hasta 2 líneas
                ellipsize = TextUtils.TruncateAt.END
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            binding.rowContainer.addView(textView)
        }
    }

    abstract fun bindRow(binding: TableRowDynamicBinding, item: T, position: Int)

    class DynamicViewHolder<T>(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (T) -> Unit,
        private val onDeleteClick: (T) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T, bindRowCallback: (TableRowDynamicBinding, T, Int) -> Unit, position: Int) {
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
