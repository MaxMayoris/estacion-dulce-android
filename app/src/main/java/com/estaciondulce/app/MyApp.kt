package com.estaciondulce.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)?.options
        } catch (e: Exception) {
            Log.e("FirebaseConfig", "Error initializing Firebase: ${e.message}")
        }
    }
}
