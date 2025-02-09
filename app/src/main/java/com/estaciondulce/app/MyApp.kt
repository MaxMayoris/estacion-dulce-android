package com.estaciondulce.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.estaciondulce.app.repository.FirestoreRepository

/**
 * Application class for global Firebase and Firestore initialization.
 */
class MyApp : Application() {

    /**
     * Initializes Firebase and starts global Firestore listeners.
     */
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Optionally log the error if needed.
        }
        FirestoreRepository.startListeners()
    }

    /**
     * Stops global Firestore listeners when the application terminates.
     */
    override fun onTerminate() {
        super.onTerminate()
        FirestoreRepository.stopListeners()
    }
}
