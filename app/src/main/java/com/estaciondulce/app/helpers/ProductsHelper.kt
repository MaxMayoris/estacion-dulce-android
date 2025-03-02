package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Product

import com.google.firebase.firestore.FirebaseFirestore

class ProductsHelper() {

    private val firestore = FirebaseFirestore.getInstance()

    fun addProduct(product: Product, onSuccess: (Product) -> Unit, onError: (Exception) -> Unit) {
        val productData = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "cost" to product.cost,
            "measure" to product.measure,
            "minimumQuantity" to product.minimumQuantity
        )
        firestore.collection("products").add(productData)
            .addOnSuccessListener { documentReference ->
                onSuccess(product.copy(id = documentReference.id))
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun updateProduct(
        productId: String,
        product: Product,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val productData = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "cost" to product.cost,
            "measure" to product.measure,
            "minimumQuantity" to product.minimumQuantity
        )
        firestore.collection("products").document(productId)
            .update(productData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }

    fun deleteProduct(productId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("products").document(productId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }

    fun updateProductStock(
        productId: String,
        delta: Double,
        updateCost: Boolean,
        newCost: Double? = null,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val productRef = firestore.collection("products").document(productId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(productRef)
            val currentStock = snapshot.getDouble("quantity") ?: 0.0
            val newStock = currentStock + delta
            transaction.update(productRef, "quantity", newStock)
            if (updateCost && newCost != null) {
                transaction.update(productRef, "cost", newCost)
            }
            null
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { exception ->
            onError(exception)
        }
    }



}
