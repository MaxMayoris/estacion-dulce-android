package com.estaciondulce.app.adapters

import android.widget.TextView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.parcelables.Movement

class MovementAdapter(
    movementList: List<Movement>,
    onRowClick: (Movement) -> Unit,
    onDeleteClick: (Movement) -> Unit,
    private val showDeleteIcon: Boolean = true,
    private val highlightAmountByType: Boolean = false,
    private val attributeGetter: (Movement) -> List<Any>
) : TableAdapter<Movement>(movementList, onRowClick, onDeleteClick) {

    override val rowIconSlots: Int = if (showDeleteIcon) 1 else 0

    override fun getCellValues(item: Movement, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Movement, position: Int) {
        val cellValues = getCellValues(item, position)
        bindRowContent(binding, cellValues)
        if (highlightAmountByType) {
            highlightAmountCell(binding, cellValues, item)
        }
        
        binding.deleteIcon.visibility = if (showDeleteIcon) android.view.View.VISIBLE else android.view.View.GONE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        configureIconSpacing(binding)
    }

    /**
     * Colors the amount cell green for sales and red for purchases.
     */
    private fun highlightAmountCell(binding: TableRowDynamicBinding, cellValues: List<Any>, item: Movement) {
        val amountIndex = cellValues.indexOfFirst { it is Double && it == item.totalAmount }
        val amountCell = binding.rowContainer.getChildAt(amountIndex) as? TextView ?: return
        val colorRes = if (item.type == EMovementType.SALE) R.color.success_green else R.color.error_red
        amountCell.setTextColor(binding.root.context.getColor(colorRes))
        amountCell.setTypeface(null, android.graphics.Typeface.BOLD)
    }
}
