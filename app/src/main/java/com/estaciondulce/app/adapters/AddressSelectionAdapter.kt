package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ItemAddressSelectionBinding
import com.estaciondulce.app.models.Address

/**
 * Adapter for displaying addresses in a selection dialog.
 */
class AddressSelectionAdapter(
    private var addresses: List<Address>,
    private val onAddressSelected: (Address) -> Unit
) : RecyclerView.Adapter<AddressSelectionAdapter.AddressViewHolder>() {

    class AddressViewHolder(private val binding: ItemAddressSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(address: Address, onAddressSelected: (Address) -> Unit) {
            android.util.Log.d("AddressSelectionAdapter", "Binding address: id=${address.id}, label=${address.label}")
            binding.addressLabelText.text = address.label
            binding.addressText.text = address.formattedAddress
            
            // Set address detail
            if (address.detail.isNotEmpty()) {
                binding.addressDetailText.text = address.detail
                binding.addressDetailText.visibility = View.VISIBLE
            } else {
                binding.addressDetailText.visibility = View.GONE
            }
            
            android.util.Log.d("AddressSelectionAdapter", "Setting click listener for address: ${address.label}")
            binding.root.setOnClickListener {
                android.util.Log.d("AddressSelectionAdapter", "Address item clicked: id=${address.id}, label=${address.label}")
                onAddressSelected(address)
                android.util.Log.d("AddressSelectionAdapter", "onAddressSelected callback called")
            }
            android.util.Log.d("AddressSelectionAdapter", "Click listener set successfully for address: ${address.label}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        android.util.Log.d("AddressSelectionAdapter", "onCreateViewHolder called for viewType: $viewType")
        val binding = ItemAddressSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        android.util.Log.d("AddressSelectionAdapter", "ViewHolder created successfully")
        return AddressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        android.util.Log.d("AddressSelectionAdapter", "onBindViewHolder called for position: $position, address: ${addresses[position].label}")
        holder.bind(addresses[position], onAddressSelected)
        android.util.Log.d("AddressSelectionAdapter", "onBindViewHolder completed for position: $position")
    }

    override fun getItemCount(): Int {
        android.util.Log.d("AddressSelectionAdapter", "getItemCount called, returning: ${addresses.size}")
        return addresses.size
    }

    fun updateAddresses(newAddresses: List<Address>) {
        android.util.Log.d("AddressSelectionAdapter", "updateAddresses called with ${newAddresses.size} addresses")
        addresses = newAddresses
        notifyDataSetChanged()
    }
}
