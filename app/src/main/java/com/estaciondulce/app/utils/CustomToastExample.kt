package com.estaciondulce.app.utils

import android.content.Context
import com.estaciondulce.app.R

/**
 * Example usage of CustomToast in different scenarios.
 * This file demonstrates how to use the CustomToast utility class.
 */
object CustomToastExample {

    /**
     * Example: Show success message when login is successful
     */
    fun showLoginSuccess(context: Context) {
        CustomToast.showSuccess(context, context.getString(R.string.login_success))
    }

    /**
     * Example: Show error message when login fails
     */
    fun showLoginError(context: Context, errorMessage: String) {
        CustomToast.showError(context, context.getString(R.string.login_error, errorMessage))
    }

    /**
     * Example: Show info message for general information
     */
    fun showInfoMessage(context: Context, message: String) {
        CustomToast.showInfo(context, message)
    }

    /**
     * Example: Show warning message for validation issues
     */
    fun showValidationWarning(context: Context, fieldName: String) {
        CustomToast.showWarning(context, "El campo '$fieldName' es obligatorio")
    }

    /**
     * Example: Show success message when product is saved
     */
    fun showProductSaved(context: Context) {
        CustomToast.showSuccess(context, "Producto guardado correctamente")
    }

    /**
     * Example: Show error message when product save fails
     */
    fun showProductSaveError(context: Context, errorMessage: String) {
        CustomToast.showError(context, "Error al guardar: $errorMessage")
    }

    /**
     * Example: Show info message for product validation
     */
    fun showProductValidationInfo(context: Context, message: String) {
        CustomToast.showInfo(context, message)
    }
}


