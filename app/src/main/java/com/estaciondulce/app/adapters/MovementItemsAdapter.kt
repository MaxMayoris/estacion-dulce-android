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
    private val getDisplayName: (collection: String, collectionId: String) -> String
) : RecyclerView.Adapter<MovementItemsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemMovementBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MovementItem, position: Int) {
            binding.itemNameTextView.text = getDisplayName(item.collection, item.collectionId)
            
            binding.quantityEditText.setText(item.quantity.toString())
            binding.quantityEditText.isEnabled = true
            binding.costEditText.setText(item.cost.toString())
            binding.costEditText.isEnabled = true

            binding.deleteItemButton.setOnClickListener {
                onDeleteClicked(position)
            }

            binding.quantityEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    val newQuantity = text.toDoubleOrNull()
                    
                    if (text.isEmpty()) {
                        if (items[position].quantity != 0.0) {
                            items[position].quantity = 0.0
                            onItemChanged()
                        }
                        return
                    }
                    
                    val decimalParts = text.split(".")
                    if (decimalParts.size == 2 && decimalParts[1].length > 3) {
                        binding.quantityEditText.removeTextChangedListener(this)
                        val truncatedText = decimalParts[0] + "." + decimalParts[1].substring(0, 3)
                        binding.quantityEditText.setText(truncatedText)
                        binding.quantityEditText.setSelection(truncatedText.length)
                        binding.quantityEditText.addTextChangedListener(this)
                        val truncatedQuantity = truncatedText.toDoubleOrNull() ?: 0.0
                        if (items[position].quantity != truncatedQuantity) {
                            items[position].quantity = truncatedQuantity
                            onItemChanged()
                        }
                        return
                    }
                    
                    if (newQuantity != null && newQuantity != items[position].quantity) {
                        items[position].quantity = newQuantity
                        onItemChanged()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            binding.costEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val newCost = s.toString().toDoubleOrNull() ?: 0.0
                    if (newCost != items[position].cost) {
                        items[position].cost = newCost
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
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}


