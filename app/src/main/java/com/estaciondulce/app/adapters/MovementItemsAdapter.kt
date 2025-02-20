package com.estaciondulce.app.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.databinding.ItemMovementBinding
import com.estaciondulce.app.models.MovementItem

class MovementItemsAdapter(
    private var items: MutableList<MovementItem>,
    private val onItemChanged: () -> Unit,  // Callback when an item is updated
    private val onDeleteClicked: (Int) -> Unit,  // Callback with the item position to delete
    // Callback to get the display name (without saving it) from collection and collectionId
    private val getDisplayName: (collection: String, collectionId: String) -> String
) : RecyclerView.Adapter<MovementItemsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemMovementBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MovementItem, position: Int) {
            // Use the callback to display the name
            binding.itemNameTextView.text = getDisplayName(item.collection, item.collectionId)
            binding.quantityEditText.setText(item.quantity.toString())
            binding.costEditText.setText(item.cost.toString())
            binding.deleteItemButton.setOnClickListener {
                onDeleteClicked(position)
            }
            binding.quantityEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val newQuantity = s.toString().toDoubleOrNull() ?: 0.0
                    if (newQuantity != item.quantity) {
                        item.quantity = newQuantity
                        onItemChanged()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            binding.costEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val newCost = s.toString().toDoubleOrNull() ?: 0.0
                    if (newCost != item.cost) {
                        item.cost = newCost
                        onItemChanged()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMovementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: MutableList<MovementItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
