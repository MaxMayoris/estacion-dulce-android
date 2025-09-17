package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ItemRecipeSearchBinding
import com.estaciondulce.app.models.Recipe

class RecipeSearchAdapter(
    private var recipes: List<Recipe>,
    private val onRecipeClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeSearchAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(val binding: ItemRecipeSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        
        holder.binding.recipeName.text = recipe.name
        holder.binding.recipeCost.text = "$${String.format("%.2f", recipe.cost)}"
        
        holder.itemView.setOnClickListener {
            onRecipeClick(recipe)
        }
    }

    override fun getItemCount(): Int = recipes.size

    fun updateRecipes(newRecipes: List<Recipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }
}












