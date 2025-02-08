package com.estaciondulce.app.repository

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.estaciondulce.app.models.Category
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.models.Section
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FirestoreRepository {

    // Firestore instance
    @SuppressLint("StaticFieldLeak")
    private val firestore = FirebaseFirestore.getInstance()

    // LiveData objects for each collection.
    val productsLiveData = MutableLiveData<List<Product>>()
    val recipesLiveData = MutableLiveData<List<Recipe>>()
    val measuresLiveData = MutableLiveData<List<Measure>>()
    val categoriesLiveData = MutableLiveData<List<Category>>()
    val sectionsLiveData = MutableLiveData<List<Section>>()

    // Listener registrations (to remove them later if needed)
    private var productsListener: ListenerRegistration? = null
    private var recipesListener: ListenerRegistration? = null
    private var measuresListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var sectionsListener: ListenerRegistration? = null

    /**
     * Starts snapshot listeners for collections.
     * This method fetches the initial data and keeps the local cache updated
     * in real time.
     */
    fun startListeners() {
        // Listen to the "products" collection.
        productsListener = firestore.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val products = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Product::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                productsLiveData.postValue(products)
            }

        // Listen to the "recipes" collection.
        recipesListener = firestore.collection("recipes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                recipesLiveData.postValue(recipes)
            }


        // Listen to the "measures" collection.
        measuresListener = firestore.collection("measures")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val measures = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Measure::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                measuresLiveData.postValue(measures)
            }

        // Listen to the "categories" collection.
        categoriesListener = firestore.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                categoriesLiveData.postValue(categories)
            }

        // Listen to the "sections" collection.
        sectionsListener = firestore.collection("sections")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val sections = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Section::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                sectionsLiveData.postValue(sections)
            }

    }

    /**
     * Stops the snapshot listeners.
     */
    fun stopListeners() {
        productsListener?.remove()
        recipesListener?.remove()
        measuresListener?.remove()
    }
}
