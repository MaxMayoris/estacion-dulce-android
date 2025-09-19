package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ItemDialogAddItemBinding
import com.estaciondulce.app.models.parcelables.ItemSearchResult
import com.estaciondulce.app.models.enums.EItemType

class DialogAddItemAdapter(
    private var items: List<ItemSearchResult>,
    private val onItemClick: (ItemSearchResult) -> Unit
) : RecyclerView.Adapter<DialogAddItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(val binding: ItemDialogAddItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemDialogAddItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        
        holder.binding.itemName.text = item.name
        
        when {
            item.collection == "custom" -> {
                holder.binding.itemIcon.setImageResource(R.drawable.ic_add)
                holder.binding.itemType.visibility = View.GONE
                holder.binding.itemCost.visibility = View.GONE
            }
            item.type == EItemType.PRODUCT -> {
                holder.binding.itemIcon.setImageResource(R.drawable.ic_product)
                holder.binding.itemType.visibility = View.GONE
                holder.binding.itemCost.visibility = View.VISIBLE
                holder.binding.itemCost.text = "$${String.format("%.2f", item.price)}"
            }
            item.type == EItemType.RECIPE -> {
                holder.binding.itemIcon.setImageResource(R.drawable.ic_recipe)
                holder.binding.itemType.visibility = View.GONE
                holder.binding.itemCost.visibility = View.VISIBLE
                holder.binding.itemCost.text = "$${String.format("%.2f", item.price)}"
            }
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ItemSearchResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
