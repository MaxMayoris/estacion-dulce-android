package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.google.android.libraries.places.api.model.AutocompletePrediction

class AddressSuggestionAdapter(
    private val suggestions: MutableList<AutocompletePrediction> = mutableListOf(),
    private val onSuggestionClick: (AutocompletePrediction) -> Unit
) : RecyclerView.Adapter<AddressSuggestionAdapter.SuggestionViewHolder>() {

    class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val addressMainText: TextView = itemView.findViewById(R.id.addressMainText)
        val addressSecondaryText: TextView = itemView.findViewById(R.id.addressSecondaryText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_address_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        
        // Set main address text (primary text)
        holder.addressMainText.text = suggestion.getPrimaryText(null)
        
        // Set secondary address text (secondary text)
        holder.addressSecondaryText.text = suggestion.getSecondaryText(null)
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onSuggestionClick(suggestion)
        }
    }

    override fun getItemCount(): Int = suggestions.size

    fun updateSuggestions(newSuggestions: List<AutocompletePrediction>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    fun clearSuggestions() {
        suggestions.clear()
        notifyDataSetChanged()
    }
}
