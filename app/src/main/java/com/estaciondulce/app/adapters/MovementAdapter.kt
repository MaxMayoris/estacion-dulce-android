package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.Movement

class MovementAdapter(
    movementList: List<Movement>,
    onRowClick: (Movement) -> Unit,
    onDeleteClick: (Movement) -> Unit,
    private val attributeGetter: (Movement) -> List<Any>
) : TableAdapter<Movement>(movementList, onRowClick, onDeleteClick) {

    override fun getCellValues(item: Movement, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Movement, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
    }
}