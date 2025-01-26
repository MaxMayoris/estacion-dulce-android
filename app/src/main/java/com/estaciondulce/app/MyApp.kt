package com.estaciondulce.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val options = FirebaseApp.initializeApp(this)?.options
            Log.d("FirebaseConfig", "Firebase initialized with Project ID: ${options?.projectId}, App ID: ${options?.applicationId}")
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Error initializing Firebase: ${e.message}")
        }
    }
}
