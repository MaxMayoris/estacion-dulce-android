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

        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val providerClassName = if (isDebuggable)
            "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
        else
            "com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory"

        val provider: AppCheckProviderFactory? = try {
            val cls = Class.forName(providerClassName)
            val getInstance = cls.getMethod("getInstance")
            @Suppress("UNCHECKED_CAST")
            getInstance.invoke(null) as AppCheckProviderFactory
        } catch (t: Throwable) {
            null
        }

        provider?.let {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(it)
        }

        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnFailureListener { Log.w("MyApp", "AppCheck token fetch failed", it) }

        com.estaciondulce.app.helpers.FirebaseFunctionsHelper.initialize(this)
        
        FirestoreRepository.startListeners()
    }

    override fun onTerminate() {
        super.onTerminate()
        FirestoreRepository.stopListeners()
    }
}
