package com.estaciondulce.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.services.MyFirebaseMessagingService

/**
 * Application class handling Firebase initialization, App Check configuration, and FCM notifications.
 */
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}
        configureFirestoreCache()

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
        
        createNotificationChannel()

        if (FirebaseAuth.getInstance().currentUser != null) {
            FirestoreRepository.startListeners()
        }
    }

    /**
     * Makes the Firestore local cache unlimited. Movements are synced incrementally, so older
     * documents are not part of any active query and the default 100 MB LRU cache could evict
     * them, silently dropping history from statistics. Must run before any other Firestore call.
     */
    private fun configureFirestoreCache() {
        try {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
        } catch (e: IllegalStateException) {
            Log.w("MyApp", "Firestore already in use, cache settings not applied", e)
        }
    }

    /**
     * Creates notification channel for Android 8.0+ to display FCM notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = MyFirebaseMessagingService.CHANNEL_ID
            val channelName = "Notificaciones Generales"
            val channelDescription = "Notificaciones de stock bajo y alertas importantes"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        FirestoreRepository.stopListeners()
    }
}
