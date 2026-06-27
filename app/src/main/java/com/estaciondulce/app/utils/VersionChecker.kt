package com.estaciondulce.app.utils

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Utility class to check the installed app version against Firestore minVersionCode
 * and force an update if the installed version is outdated.
 */
object VersionChecker {

    fun checkForUpdates(activity: AppCompatActivity) {
        FirebaseFirestore.getInstance().collection("settings").document("app_config")
            .get()
            .addOnSuccessListener { document ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                
                val minVersionCode = document.getLong("minVersionCode") ?: 0L
                val updateUrl = document.getString("updateUrl") ?: "https://play.google.com/apps/internaltest/4701724229366743626"
                
                val currentVersionCode = try {
                    val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                } catch (e: Exception) {
                    0L
                }

                if (currentVersionCode < minVersionCode) {
                    showUpdateDialog(activity, updateUrl)
                }
            }
    }

    private fun showUpdateDialog(activity: AppCompatActivity, updateUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle("Actualización obligatoria")
            .setMessage("Hay una nueva versión disponible. Es necesario actualizar para continuar usando la aplicación.")
            .setCancelable(false)
            .setPositiveButton("Actualizar") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
                    activity.startActivity(intent)
                }
                activity.finish()
            }
            .show()
    }
}
