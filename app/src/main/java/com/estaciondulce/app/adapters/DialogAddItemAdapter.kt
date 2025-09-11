package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.ItemEntry
import com.estaciondulce.app.databinding.ItemDialogAddItemBinding
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe

class DialogAddItemAdapter(
    private var items: List<ItemEntry>,
    private val products: List<Product>,
    private val recipes: List<Recipe>,
    private val isPurchase: Boolean, // true = purchase (products), false = sale (recipes)
    private val onItemClick: (ItemEntry) -> Unit
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
        
        if (item.collection == "custom") {
            holder.binding.itemIcon.setImageResource(R.drawable.ic_add)
            holder.binding.itemType.visibility = View.GONE
            holder.binding.itemCost.visibility = View.GONE
        } else if (isPurchase) {
            holder.binding.itemIcon.setImageResource(R.drawable.ic_product)
            holder.binding.itemType.visibility = View.GONE // No need to show "Producto" badge
            holder.binding.itemCost.visibility = View.VISIBLE
            holder.binding.itemCost.text = "$${String.format("%.2f", getProductCost(item.collectionId))}"
        } else {
            holder.binding.itemIcon.setImageResource(R.drawable.ic_recipe)
            holder.binding.itemType.visibility = View.GONE // No need to show "Receta" badge
            holder.binding.itemCost.visibility = View.VISIBLE
            holder.binding.itemCost.text = "$${String.format("%.2f", getRecipeSalePrice(item.collectionId))}"
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ItemEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun getProductCost(productId: String): Double {
        return products.find { it.id == productId }?.cost ?: 0.0
    }

    private fun getRecipeSalePrice(recipeId: String): Double {
        return recipes.find { it.id == recipeId }?.salePrice ?: 0.0
    }
}
