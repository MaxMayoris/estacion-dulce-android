package com.estaciondulce.app.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.estaciondulce.app.R

/**
 * Custom Toast component with styled layout and icons for different message types.
 * Provides a consistent visual experience across the app.
 */
class CustomToast private constructor(
    private val context: Context,
    private val message: String,
    private val type: ToastType,
    private val duration: Int
) {

    enum class ToastType {
        SUCCESS, ERROR, INFO, WARNING
    }

    companion object {
        /**
         * Shows a success toast message
         * @param duration Duration in seconds (default: 3 seconds)
         */
        fun showSuccess(context: Context, message: String, duration: Int = 3) {
            CustomToast(context, message, ToastType.SUCCESS, duration).show()
        }

        /**
         * Shows an error toast message
         * @param duration Duration in seconds (default: 3 seconds)
         */
        fun showError(context: Context, message: String, duration: Int = 3) {
            CustomToast(context, message, ToastType.ERROR, duration).show()
        }

        /**
         * Shows an info toast message
         * @param duration Duration in seconds (default: 3 seconds)
         */
        fun showInfo(context: Context, message: String, duration: Int = 3) {
            CustomToast(context, message, ToastType.INFO, duration).show()
        }

        /**
         * Shows a warning toast message
         * @param duration Duration in seconds (default: 3 seconds)
         */
        fun showWarning(context: Context, message: String, duration: Int = 3) {
            CustomToast(context, message, ToastType.WARNING, duration).show()
        }
    }

    /**
     * Shows the custom toast using modern API
     */
    fun show() {
        val customView = createCustomView()
        
        // Use modern Toast API
        val toast = Toast(context)
        
        // Convert seconds to Toast duration constants
        val toastDuration = when {
            duration <= 2 -> Toast.LENGTH_SHORT
            else -> Toast.LENGTH_LONG
        }
        toast.duration = toastDuration
        
        // Add click listener to dismiss toast manually
        customView.setOnClickListener {
            toast.cancel()
        }
        
        // For Android 11+ (API 30+), use the modern approach
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Use the new API
            toast.addCallback(object : Toast.Callback() {
                override fun onToastShown() {
                    // Toast shown successfully
                }
                
                override fun onToastHidden() {
                    // Toast hidden
                }
            })
        }
        
        // Set the custom view
        @Suppress("DEPRECATION")
        toast.view = customView
        
        toast.show()
    }

    /**
     * Creates the custom view for the toast
     */
    private fun createCustomView(): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.custom_toast, null)

        val iconView = view.findViewById<ImageView>(R.id.toastIcon)
        val messageView = view.findViewById<TextView>(R.id.toastMessage)

        // Set message
        messageView.text = message

        // Set icon and colors based on type
        when (type) {
            ToastType.SUCCESS -> {
                iconView.setImageResource(R.drawable.ic_check_circle)
                iconView.setColorFilter(context.getColor(R.color.success_green))
                view.setBackgroundResource(R.drawable.toast_success_background)
            }
            ToastType.ERROR -> {
                iconView.setImageResource(R.drawable.ic_error)
                iconView.setColorFilter(context.getColor(R.color.error_red))
                view.setBackgroundResource(R.drawable.toast_error_background)
            }
            ToastType.INFO -> {
                iconView.setImageResource(R.drawable.ic_info)
                iconView.setColorFilter(context.getColor(R.color.info_blue))
                view.setBackgroundResource(R.drawable.toast_info_background)
            }
            ToastType.WARNING -> {
                iconView.setImageResource(R.drawable.ic_warning)
                iconView.setColorFilter(context.getColor(R.color.warning_orange))
                view.setBackgroundResource(R.drawable.toast_warning_background)
            }
        }

        return view
    }
}
