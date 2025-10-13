package com.estaciondulce.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.services.MyFirebaseMessagingService

/**
 * Application class handling Firebase initialization, App Check configuration, and FCM notifications.
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
        
        createNotificationChannel()
        
        FirestoreRepository.startListeners()
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
