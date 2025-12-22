package com.estaciondulce.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.ProductEditActivity
import com.estaciondulce.app.models.parcelables.Product
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service to handle FCM push notifications for low stock alerts and other events.
 * Handles both foreground and background messages.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            handleDataPayload(remoteMessage.data)
        } else {
        }

        remoteMessage.notification?.let {
        }
    }

    /**
     * Handles data payload from FCM message and creates appropriate notification.
     */
    private fun handleDataPayload(data: Map<String, String>) {
        val screen = data["screen"]
        val productId = data["productId"]
        val title = data["title"]?.trim() ?: "Estación Dulce"
        val body = data["body"]?.trim() ?: ""

        if (title.isEmpty() && body.isEmpty()) {
            return
        }

        if (body.isEmpty()) {
            return
        }

        when (screen) {
            "product_detail" -> {
                if (!productId.isNullOrEmpty()) {
                    showNotification(title, body, productId)
                } else {
                }
            }
            "kitchen_orders" -> {
                showNotification(title, body, null)
            }
            "home" -> {
                showNotification(title, body, null)
            }
            else -> {
                showNotification(title, body, null)
            }
        }
    }

    /**
     * Shows a notification with optional deep link to ProductEditActivity.
     */
    private fun showNotification(title: String, body: String, productId: String?) {
        if (title.isEmpty() || body.isEmpty()) {
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = if (!productId.isNullOrEmpty()) {
            Intent(this, ProductEditActivity::class.java).apply {
                putExtra("PRODUCT_ID", productId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val logoBitmap = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_logo_ed_circle_192x192)
        } catch (e: Exception) {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        logoBitmap?.let {
            notificationBuilder.setLargeIcon(it)
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Called when a new FCM token is generated.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID = "estacion_dulce_general"
        private const val NOTIFICATION_ID = 1001
    }
}

