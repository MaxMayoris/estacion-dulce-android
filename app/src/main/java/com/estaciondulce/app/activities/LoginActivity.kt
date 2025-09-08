package com.estaciondulce.app.activities

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.estaciondulce.app.R
import com.estaciondulce.app.helpers.StorageHelper
import com.estaciondulce.app.utils.CustomToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity for user login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storageHelper: StorageHelper
    private val skipLoginForDebug = false

    /**
     * Initializes the login activity. If skipLoginForDebug is true, skips login.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply custom theme
        setTheme(R.style.Theme_EstacionDulceApp_Login)

        if (skipLoginForDebug) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()
        storageHelper = StorageHelper()

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val rootLayout = findViewById<ConstraintLayout>(R.id.loginRootLayout)
        val footerText = findViewById<TextView>(R.id.footerText)

        // Set dynamic footer text with version
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            footerText.text = "Estacion Dulce Manager - v$versionName · by Maksee"
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback to default text if version cannot be retrieved
            footerText.text = "Estacion Dulce Manager by Maksee"
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                CustomToast.showError(this, "Por favor completa todos los campos.")
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        CustomToast.showSuccess(this, "Inicio de sesión exitoso!")
                        
                        // Navigate to HomeActivity after a short delay
                        rootLayout.postDelayed({
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }, 2000) // 2 second delay to show the storage test result
                    } else {
                        val errorMessage = task.exception?.message ?: "Error en el inicio de sesión."
                        CustomToast.showError(this, errorMessage)
                    }
                }
        }
    }
    
}
