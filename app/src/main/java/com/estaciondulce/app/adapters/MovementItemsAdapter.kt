package com.estaciondulce.app.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.databinding.ItemMovementBinding
import com.estaciondulce.app.models.parcelables.MovementItem

class MovementItemsAdapter(
    private var items: MutableList<MovementItem>,
    private val onItemChanged: () -> Unit,  // Callback when an item is updated
    private val onDeleteClicked: (Int) -> Unit,  // Callback with the item position to delete
    private val getDisplayName: (collection: String, collectionId: String) -> String
) : RecyclerView.Adapter<MovementItemsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemMovementBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        private var quantityWatcher: TextWatcher? = null
        private var costWatcher: TextWatcher? = null
        
        fun bind(item: MovementItem, position: Int) {
            binding.itemNameTextView.text = getDisplayName(item.collection, item.collectionId)
            
            quantityWatcher?.let { binding.quantityEditText.removeTextChangedListener(it) }
            costWatcher?.let { binding.costEditText.removeTextChangedListener(it) }
            
            binding.quantityEditText.setText(item.quantity.toString())
            binding.quantityEditText.isEnabled = true
            binding.costEditText.setText(item.cost.toString())
            binding.costEditText.isEnabled = true

            binding.deleteItemButton.setOnClickListener {
                onDeleteClicked(position)
            }

            quantityWatcher = object : TextWatcher {
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
                        quantityWatcher?.let { binding.quantityEditText.removeTextChangedListener(it) }
                        val truncatedText = decimalParts[0] + "." + decimalParts[1].substring(0, 3)
                        binding.quantityEditText.setText(truncatedText)
                        binding.quantityEditText.setSelection(truncatedText.length)
                        quantityWatcher?.let { binding.quantityEditText.addTextChangedListener(it) }
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
            }

            costWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    val newCost = text.toDoubleOrNull() ?: 0.0
                    
                    if (text.isEmpty()) {
                        if (items[position].cost != 0.01) {
                            items[position].cost = 0.01
                            binding.costEditText.setText("0.01")
                            binding.costEditText.setSelection(binding.costEditText.text.length)
                            onItemChanged()
                        }
                        return
                    }
                    
                    if (newCost <= 0.0) {
                        binding.costEditText.removeTextChangedListener(this)
                        binding.costEditText.setText("0.01")
                        binding.costEditText.setSelection(binding.costEditText.text.length)
                        binding.costEditText.addTextChangedListener(this)
                        if (items[position].cost != 0.01) {
                            items[position].cost = 0.01
                            onItemChanged()
                        }
                        return
                    }
                    
                    if (newCost != items[position].cost) {
                        items[position].cost = newCost
                        onItemChanged()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            
            quantityWatcher?.let { binding.quantityEditText.addTextChangedListener(it) }
            costWatcher?.let { binding.costEditText.addTextChangedListener(it) }
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


