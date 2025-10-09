package com.estaciondulce.app.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.estaciondulce.app.R

object ConfirmationDialog {

    /**
     * Shows a modern confirmation dialog with customizable message and buttons.
     * 
     * @param context The context to show the dialog in
     * @param title The title of the dialog
     * @param message The confirmation message
     * @param confirmButtonText Text for the confirm button (default: "Confirmar")
     * @param cancelButtonText Text for the cancel button (default: "Cancelar")
     * @param onConfirm Callback when user confirms
     * @param onCancel Optional callback when user cancels (default: null)
     */
    fun show(
        context: Context,
        title: String = "Confirmar acción",
        message: String,
        confirmButtonText: String = "Confirmar",
        cancelButtonText: String = "Cancelar",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirmation, null)
        
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmButton)
        
        titleView.text = title
        messageView.text = message
        cancelButton.text = cancelButtonText
        confirmButton.text = confirmButtonText
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        cancelButton.setOnClickListener {
            onCancel?.invoke()
            dialog.dismiss()
        }
        
        confirmButton.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        
        dialog.show()
    }
}

