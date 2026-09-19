package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.parcelables.Event

class EventAdapter(
    eventList: List<Event>,
    onRowClick: (Event) -> Unit,
    onDeleteClick: (Event) -> Unit,
    private val onEditClick: (Event) -> Unit,
    private val attributeGetter: (Event) -> List<Any>
) : TableAdapter<Event>(eventList, onRowClick, onDeleteClick) {

    override fun getCellValues(item: Event, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Event, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
        
        binding.deleteIcon.visibility = android.view.View.VISIBLE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        configureIconSpacing(binding)
    }
}
