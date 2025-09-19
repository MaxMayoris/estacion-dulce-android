package com.estaciondulce.app.adapters

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
        bindRowContent(binding, getCellValues(item, position))
        
        // Show only delete icon for products
        binding.deleteIcon.visibility = android.view.View.VISIBLE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        // Configure dynamic icon spacing
        configureIconSpacing(binding)
    }
}
