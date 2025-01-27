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
        // Initialize the dialog with the custom layout
        val view = LayoutInflater.from(context).inflate(R.layout.custom_loader, null)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(false) // Prevent closing while loading
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK)) // Full black background
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
        }

        // Get references to UI components
        logo = dialog.findViewById(R.id.loaderLogo)
        loadingText = dialog.findViewById(R.id.loaderText)

        // Add rotation animation to the logo
        val rotation = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f)
        rotation.duration = 1000 // 1 second
        rotation.repeatCount = ObjectAnimator.INFINITE
        rotation.start()
    }

    // Show the loader
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
