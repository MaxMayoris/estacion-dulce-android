package com.estaciondulce.app.utils

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.estaciondulce.app.R

class CustomLoader(context: Context) {

    private val dialog: Dialog = Dialog(context)
    private val logo: ImageView
    private val loadingText: TextView

    init {
        // Inflate the custom loader layout
        val view = LayoutInflater.from(context).inflate(R.layout.custom_loader, null)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(false) // Prevent closing while loading

        // Set the window background to transparent so no black background shows
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Remove dimming if desired (or adjust the dim amount)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        // Get UI elements
        logo = dialog.findViewById(R.id.loaderLogo)
        loadingText = dialog.findViewById(R.id.loaderText)

        // Set up the rotation animation for the logo
        val rotation = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f)
        rotation.duration = 1000 // 1 second per rotation
        rotation.repeatCount = ObjectAnimator.INFINITE
        rotation.start()
    }

    // Show the loader with an optional message override
    fun show(loadingMessage: String? = null) {
        loadingMessage?.let { setLoadingText(it) }
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    // Hide the loader
    fun hide() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    // Set custom loading text
    fun setLoadingText(message: String) {
        loadingText.text = message
    }
}
