package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.estaciondulce.app.databinding.ItemRecipeImageBinding

/**
 * Adapter for displaying images in a horizontal gallery without delete functionality.
 * Used for read-only image display in KitchenOrderEditActivity.
 */
class ImageViewOnlyAdapter(
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<ImageViewOnlyAdapter.ImageViewHolder>() {

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

            // Hide delete button for read-only mode
            binding.deleteButton.visibility = android.view.View.GONE

            // Set click listener on the image itself
            binding.imageView.setOnClickListener {
                onImageClick(imageUrl)
            }
        }
    }
}

