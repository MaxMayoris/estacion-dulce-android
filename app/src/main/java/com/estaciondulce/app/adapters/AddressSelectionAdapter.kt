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
            binding.addressLabelText.text = address.label
            binding.addressText.text = address.formattedAddress
            
            // Set address detail
            if (address.detail.isNotEmpty()) {
                binding.addressDetailText.text = address.detail
                binding.addressDetailText.visibility = View.VISIBLE
            } else {
                binding.addressDetailText.visibility = View.GONE
            }
            
            binding.root.setOnClickListener {
                onAddressSelected(address)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = ItemAddressSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AddressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(addresses[position], onAddressSelected)
    }

    override fun getItemCount(): Int {
        return addresses.size
    }

    fun updateAddresses(newAddresses: List<Address>) {
        addresses = newAddresses
        notifyDataSetChanged()
    }
}
