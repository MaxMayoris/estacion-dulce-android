package com.estaciondulce.app.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding

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

    /**
     * Returns the list of cell values for the given item.
     */
    abstract fun getCellValues(item: T, position: Int): List<Any>

    /**
     * Binds cell views with one line and ellipsis if the content is too long.
     */
    protected fun bindRowContent(binding: TableRowDynamicBinding, cellValues: List<Any>) {
        binding.rowContainer.removeAllViews()
        for (value in cellValues) {
            val textView = TextView(binding.root.context).apply {
                text = when (value) {
                    is Double -> String.format("%.2f", value)
                    else -> value.toString()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(8, 8, 8, 8)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setTextColor(binding.root.context.getColor(android.R.color.white))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            binding.rowContainer.addView(textView)
        }
    }

    /**
     * Binds the row for a given item. Must be implemented by concrete adapters.
     */
    abstract fun bindRow(binding: TableRowDynamicBinding, item: T, position: Int)

    class DynamicViewHolder<T>(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (T) -> Unit,
        private val onDeleteClick: (T) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T, bindRowCallback: (TableRowDynamicBinding, T, Int) -> Unit, position: Int) {
            bindRowCallback(binding, item, position)
            val isEvenRow = position % 2 == 0
            binding.root.setBackgroundColor(
                if (isEvenRow)
                    binding.root.context.getColor(R.color.purple_400)
                else
                    binding.root.context.getColor(R.color.purple_200)
            )
            binding.root.setOnClickListener { onRowClick(item) }
            binding.deleteIcon.setOnClickListener { onDeleteClick(item) }
        }
    }
}
