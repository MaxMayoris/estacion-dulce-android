package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.dtos.ProductDTO
import com.estaciondulce.app.models.mappers.toDTO
import com.estaciondulce.app.models.mappers.toMap
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

class ProductsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

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

    /**
     * Sets the stock (quantity) of the given products to 0 using batched writes
     * (Firestore allows at most 500 operations per batch).
     * @param products Products whose stock should be reset
     * @param onSuccess Called once every batch has been committed
     * @param onError Called with the first error that occurs
     */
    fun resetStocks(products: List<Product>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (products.isEmpty()) {
            onSuccess()
            return
        }
        val commits = products.chunked(MAX_BATCH_OPERATIONS).map { chunk ->
            val batch = db.batch()
            chunk.forEach { product ->
                batch.update(db.collection("products").document(product.id), "quantity", 0.0)
            }
            batch.commit()
        }
        Tasks.whenAll(commits)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deleteProduct(productId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "products",
            documentId = productId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    companion object {
        private const val MAX_BATCH_OPERATIONS = 500
    }
}
