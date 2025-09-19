package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.models.parcelables.Person

/**
 * Adapter for displaying persons in a search dialog.
 */
class PersonSearchAdapter(
    private var persons: List<Person>,
    private val onPersonSelected: (Person) -> Unit
) : RecyclerView.Adapter<PersonSearchAdapter.PersonViewHolder>() {

    class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val personNameText: TextView = itemView.findViewById(R.id.personNameText)
        val personTypeText: TextView = itemView.findViewById(R.id.personTypeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_search, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = persons[position]
        holder.personNameText.text = "${person.name} ${person.lastName}"
        
        // Show formatted phone number instead of person type
        val formattedPhone = if (person.phones.isNotEmpty()) {
            val firstPhone = person.phones.first()
            if (firstPhone.phoneNumberPrefix.isNotEmpty() && firstPhone.phoneNumberSuffix.isNotEmpty()) {
                "+${firstPhone.phoneNumberPrefix}${firstPhone.phoneNumberSuffix}"
            } else {
                "Sin teléfono"
            }
        } else {
            "Sin teléfono"
        }
        holder.personTypeText.text = formattedPhone

        holder.itemView.setOnClickListener {
            onPersonSelected(person)
        }
    }

    override fun getItemCount(): Int = persons.size

    fun updatePersons(newPersons: List<Person>) {
        persons = newPersons
        notifyDataSetChanged()
    }
}
