package com.estaciondulce.app.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.databinding.TableRowDynamicBinding

abstract class TableAdapter<T>(
    private var dataList: List<T>,
    private val onRowClick: (T) -> Unit, // Callback for row clicks
    private val onDeleteClick: (T) -> Unit // Callback for delete action
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

    abstract fun bindRow(binding: TableRowDynamicBinding, item: T, position: Int)

    class DynamicViewHolder<T>(
        private val binding: TableRowDynamicBinding,
        private val onRowClick: (T) -> Unit,
        private val onDeleteClick: (T) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T, bindRowCallback: (TableRowDynamicBinding, T, Int) -> Unit, position: Int) {
            bindRowCallback(binding, item, position) // Call the bindRow method
            binding.root.setOnClickListener {
                onRowClick(item) // Trigger row click callback
            }

            binding.deleteIcon.setOnClickListener {
                onDeleteClick(item) // Trigger delete callback
            }
        }
    }
}
