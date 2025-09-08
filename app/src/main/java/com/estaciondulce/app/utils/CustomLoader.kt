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

/**
 * Custom loading dialog with animated logo and customizable text.
 */
class CustomLoader(context: Context) {

    private val dialog: Dialog = Dialog(context)
    private val logo: ImageView
    private val loadingText: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.custom_loader, null)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        logo = dialog.findViewById(R.id.loaderLogo)
        loadingText = dialog.findViewById(R.id.loaderText)

        val rotation = ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f)
        rotation.duration = 1000
        rotation.repeatCount = ObjectAnimator.INFINITE
        rotation.start()
    }

    fun show(loadingMessage: String? = null) {
        loadingMessage?.let { setLoadingText(it) }
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun hide() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    fun setLoadingText(message: String) {
        loadingText.text = message
    }
}
