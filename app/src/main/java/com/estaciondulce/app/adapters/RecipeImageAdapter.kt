package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.estaciondulce.app.databinding.ItemRecipeImageBinding

/**
 * Adapter for displaying recipe images in a horizontal gallery with delete functionality.
 */
class RecipeImageAdapter(
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<RecipeImageAdapter.ImageViewHolder>() {

    private var images: List<String> = emptyList()

    fun updateImages(newImages: List<String>) {
        images = newImages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemRecipeImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount(): Int = images.size

    inner class ImageViewHolder(
        private val binding: ItemRecipeImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            Glide.with(binding.root.context)
                .load(imageUrl)
                .centerCrop()
                .into(binding.imageView)

            binding.deleteButton.setOnClickListener {
                onDeleteClick(imageUrl)
            }
        }
    }
}

