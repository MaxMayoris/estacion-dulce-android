package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.Person

class PersonAdapter(
    personList: List<Person>,
    onRowClick: (Person) -> Unit,
    onDeleteClick: (Person) -> Unit,
    private val attributeGetter: (Person) -> List<Any>
) : TableAdapter<Person>(personList, onRowClick, onDeleteClick) {

    override fun getCellValues(item: Person, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Person, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
        
        // Show only delete icon for persons
        binding.deleteIcon.visibility = android.view.View.VISIBLE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        // Configure dynamic icon spacing
        configureIconSpacing(binding)
    }
}
