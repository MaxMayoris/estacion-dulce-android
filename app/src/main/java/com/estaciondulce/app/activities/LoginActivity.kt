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
import com.estaciondulce.app.utils.CustomLoader
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity for user login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storageHelper: StorageHelper
    private lateinit var customLoader: CustomLoader
    private val skipLoginForDebug = true

    /**
     * Initializes the login activity. If skipLoginForDebug is true, skips login.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setTheme(R.style.Theme_EstacionDulceApp_Login)

        if (skipLoginForDebug) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()
        storageHelper = StorageHelper()
        customLoader = CustomLoader(this)

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val rootLayout = findViewById<ConstraintLayout>(R.id.loginRootLayout)
        val footerText = findViewById<TextView>(R.id.footerText)

        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            footerText.text = "Estacion Dulce Manager - v$versionName · by Maksee"
        } catch (e: PackageManager.NameNotFoundException) {
            footerText.text = "Estacion Dulce Manager by Maksee"
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                CustomToast.showError(this, "Por favor completa todos los campos.")
                return@setOnClickListener
            }

            customLoader.show()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    customLoader.hide()
                    
                    if (task.isSuccessful) {
                        CustomToast.showSuccess(this, "Inicio de sesión exitoso!")
                        
                        rootLayout.postDelayed({
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }, 2000)
                    } else {
                        val errorMessage = task.exception?.message ?: "Error en el inicio de sesión."
                        CustomToast.showError(this, errorMessage)
                    }
                }
        }
    }
    
}
