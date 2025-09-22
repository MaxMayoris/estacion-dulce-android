package com.estaciondulce.app.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.estaciondulce.app.databinding.ItemCarouselImageBinding

/**
 * Adapter for displaying images in a ViewPager2 carousel
 */
class ImageCarouselAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<ImageCarouselAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        Log.d("ImageCarouselAdapter", "onCreateViewHolder called")
        val binding = ItemCarouselImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Log.d("ImageCarouselAdapter", "onBindViewHolder called for position: $position")
        holder.bind(images[position])
    }

    override fun getItemCount(): Int {
        Log.d("ImageCarouselAdapter", "getItemCount called, returning: ${images.size}")
        return images.size
    }

    inner class ImageViewHolder(
        private val binding: ItemCarouselImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            Log.d("ImageCarouselAdapter", "Loading image: $imageUrl")
            
            // Clear any previous image
            binding.imageView.setImageDrawable(null)
            
            Glide.with(binding.root.context)
                .load(imageUrl)
                .centerInside()
                .transition(DrawableTransitionOptions.withCrossFade(300))
                .into(binding.imageView)
        }
    }
}
