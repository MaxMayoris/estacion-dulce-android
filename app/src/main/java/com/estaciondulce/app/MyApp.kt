package com.estaciondulce.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.estaciondulce.app.repository.FirestoreRepository

/**
 * Application class handling Firebase initialization and App Check configuration.
 */
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase (safe even if plugin already does it)
        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}

        // Choose provider based on build type (debug -> Debug; release -> Play Integrity)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val providerClassName = if (isDebuggable)
            "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
        else
            "com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory"

        // Load via reflection to avoid "Unresolved reference" errors
        val provider: AppCheckProviderFactory? = try {
            val cls = Class.forName(providerClassName)
            val getInstance = cls.getMethod("getInstance")
            @Suppress("UNCHECKED_CAST")
            getInstance.invoke(null) as AppCheckProviderFactory
        } catch (t: Throwable) {
            Log.w("MyApp", "AppCheck provider not available: $providerClassName (${t.message})")
            null
        }

        provider?.let {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(it)
        }

        // Optional: trigger token fetch early to surface errors
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnFailureListener { Log.w("MyApp", "AppCheck token fetch failed", it) }

        // Start listeners only after configuring App Check
        FirestoreRepository.startListeners()
    }

    override fun onTerminate() {
        super.onTerminate()
        FirestoreRepository.stopListeners()
    }
}
