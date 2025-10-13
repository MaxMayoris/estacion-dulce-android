package com.estaciondulce.app.utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.estaciondulce.app.R

object DeleteConfirmationDialog {

    /**
     * Shows a modern delete confirmation dialog with customizable item name.
     * 
     * @param context The context to show the dialog in
     * @param itemName The name of the item to be deleted (e.g., "Tarta de manzana")
     * @param itemType The type of item (e.g., "producto", "receta", "persona", "movimiento")
     * @param onConfirm Callback when user confirms deletion
     * @param onCancel Optional callback when user cancels (default: null)
     */
    fun show(
        context: Context,
        itemName: String,
        itemType: String = "elemento",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_confirmation, null)
        
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.deleteButton)
        
        messageView.text = context.getString(R.string.delete_confirmation, itemType, itemName)
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        cancelButton.setOnClickListener {
            onCancel?.invoke()
            dialog.dismiss()
        }
        
        deleteButton.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Convenience method for showing delete confirmation without item name.
     */
    fun showGeneric(
        context: Context,
        itemType: String = "elemento",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        show(context, "", itemType, onConfirm, onCancel)
    }
}
