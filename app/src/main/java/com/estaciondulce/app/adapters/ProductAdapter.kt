package com.estaciondulce.app.adapters

import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.Product

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
    }
}
