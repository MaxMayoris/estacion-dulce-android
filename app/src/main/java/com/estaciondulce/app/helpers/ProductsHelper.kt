package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product

class ProductsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun fetchProducts(onSuccess: (List<Product>) -> Unit, onError: (Exception) -> Unit) {
        genericHelper.fetchCollectionWithToObject(
            collectionName = "products",
            clazz = Product::class.java,
            onSuccess = { products ->
                val productsWithIds = products.map { product ->
                    product.copy(id = product.id) // Ensure the ID is set correctly
                }
                onSuccess(productsWithIds)
            },
            onError = onError
        )
    }

    fun addProduct(product: Product, onSuccess: (Product) -> Unit, onError: (Exception) -> Unit) {
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
                onSuccess(product.copy(id = documentId)) // Return the product with the new ID
            },
            onError = onError
        )
    }


    fun updateProduct(productId: String, product: Product, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
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
