package com.estaciondulce.app.adapters

import android.widget.TextView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.parcelables.Product

class ProductAdapter(
    productList: List<Product>,
    onRowClick: (Product) -> Unit,
    onDeleteClick: (Product) -> Unit,
    private val attributeGetter: (Product) -> List<Any>
) : TableAdapter<Product>(productList, onRowClick, onDeleteClick) {

    override fun getCellValues(item: Product, position: Int): List<Any> {
        return attributeGetter(item)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Product, position: Int) {
        val cellValues = getCellValues(item, position)
        bindRowContent(binding, cellValues)
        highlightStockCell(binding, cellValues, item)
        
        binding.deleteIcon.visibility = android.view.View.VISIBLE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        configureIconSpacing(binding)
    }

    /**
     * Colors the stock cell red when stock is negative and orange when it is below the minimum quantity.
     */
    private fun highlightStockCell(binding: TableRowDynamicBinding, cellValues: List<Any>, item: Product) {
        val stockIndex = cellValues.indexOfFirst { it is Double && it == item.quantity }
        val stockCell = binding.rowContainer.getChildAt(stockIndex) as? TextView ?: return
        val colorRes = when {
            item.quantity < 0 -> R.color.error_red_dark
            item.quantity < item.minimumQuantity -> R.color.warning_orange_dark
            else -> return
        }
        stockCell.setTextColor(binding.root.context.getColor(colorRes))
        stockCell.setTypeface(null, android.graphics.Typeface.BOLD)
    }
}
