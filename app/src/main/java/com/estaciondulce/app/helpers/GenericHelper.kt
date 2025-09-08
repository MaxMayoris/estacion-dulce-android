package com.estaciondulce.app.helpers

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Generic Firestore operations for CRUD operations across collections.
 */
class GenericHelper(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun addDocument(
        collectionName: String,
        data: Map<String, Any?>,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .add(data)
            .addOnSuccessListener { documentReference ->
                onSuccess(documentReference.id)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun updateDocument(
        collectionName: String,
        documentId: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .update(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

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

    fun getDocument(
        collectionName: String,
        documentId: String,
        onSuccess: (DocumentSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    onSuccess(document)
                } else {
                    onError(Exception("Document not found"))
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
