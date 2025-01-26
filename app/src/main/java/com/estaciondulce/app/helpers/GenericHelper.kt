package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Identifiable
import com.google.firebase.firestore.FirebaseFirestore

class GenericHelper(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    // Fetch collection as generic objects
    fun <T> fetchCollection(
        collectionName: String,
        mapToEntity: (id: String, data: Map<String, Any?>) -> T,
        onSuccess: (List<T>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .get()
            .addOnSuccessListener { documents ->
                val entities = documents.mapNotNull { document ->
                    mapToEntity(document.id, document.data)
                }
                onSuccess(entities)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun <T> fetchCollectionWithToObject(
        collectionName: String,
        clazz: Class<T>,
        onSuccess: (List<T>) -> Unit,
        onError: (Exception) -> Unit
    ) where T : Identifiable {
        db.collection(collectionName)
            .get()
            .addOnSuccessListener { documents ->
                val entities = documents.mapNotNull { document ->
                    document.toObject(clazz).apply {
                        this.id = document.id // Assign the document ID
                    }
                }
                onSuccess(entities)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

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
