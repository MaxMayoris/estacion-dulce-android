package com.estaciondulce.app.helpers

import com.google.firebase.firestore.FirebaseFirestore

class GenericHelper(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    // Add a document to a collection
    fun addDocument(
        collectionName: String,
        data: Map<String, Any?>,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .add(data)
            .addOnSuccessListener { documentReference ->
                onSuccess(documentReference.id) // Return the document ID
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    // Update a document in a collection
    fun updateDocument(
        collectionName: String,
        documentId: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    // Delete a document in a collection
    fun deleteDocument(
        collectionName: String,
        documentId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }
}
