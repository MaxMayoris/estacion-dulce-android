package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.dtos.ProductDTO
import com.estaciondulce.app.models.mappers.toDTO
import com.estaciondulce.app.models.mappers.toMap

class ProductsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addProduct(product: Product, onSuccess: (Product) -> Unit, onError: (Exception) -> Unit) {
        if (product.name.isBlank() || product.measure.isBlank()) {
            onError(Exception("El producto debe tener nombre y medida"))
            return
        }

        val productDTO = product.toDTO()
        val productData = productDTO.toMap()

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

        val productDTO = product.toDTO()
        val productData = productDTO.toMap()

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
