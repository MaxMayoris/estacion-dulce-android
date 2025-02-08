package com.estaciondulce.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.estaciondulce.app.repository.FirestoreRepository

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Error initializing Firebase: ${e.message}")
        }

        // Start global Firestore listeners.
        FirestoreRepository.startListeners()
    }

    // Optionally, if you want to stop the listeners when the app terminates:
    override fun onTerminate() {
        super.onTerminate()
        FirestoreRepository.stopListeners()
    }
}
