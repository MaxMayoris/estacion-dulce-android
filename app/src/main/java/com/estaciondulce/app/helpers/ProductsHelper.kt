package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product

class ProductsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addProduct(product: Product, onSuccess: (Product) -> Unit, onError: (Exception) -> Unit) {
        if (product.name.isBlank() || product.measure.isBlank()) {
            onError(Exception("El producto debe tener nombre y medida"))
            return
        }

        val productData = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "cost" to product.cost,
            "measure" to product.measure,
            "minimumQuantity" to product.minimumQuantity
        )

        genericHelper.addDocument(
            collectionName = "products",
            data = productData,
            onSuccess = { documentId ->
                onSuccess(product.copy(id = documentId))
            },
            onError = onError
        )
    }

    fun updateProduct(
        productId: String,
        product: Product,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (product.name.isBlank() || product.measure.isBlank()) {
            onError(Exception("El producto debe tener nombre y medida"))
            return
        }

        val productData = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "cost" to product.cost,
            "measure" to product.measure,
            "minimumQuantity" to product.minimumQuantity
        )

        genericHelper.updateDocument(
            collectionName = "products",
            documentId = productId,
            data = productData,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun deleteProduct(productId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "products",
            documentId = productId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

}
