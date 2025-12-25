package com.estaciondulce.app.activities

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Activity for user login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storageHelper: StorageHelper
    private lateinit var customLoader: CustomLoader
    private val skipLoginForDebug = false

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
            footerText.text = getString(R.string.login_footer, versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            footerText.text = getString(R.string.login_footer_fallback)
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
                        CustomToast.showSuccess(this, getString(R.string.login_success))
                        
                        subscribeToLowStockTopic()
                        subscribeToPendingOrdersTopic()
                        
                        rootLayout.postDelayed({
                            val homeIntent = Intent(this, HomeActivity::class.java)
                            val navigateToFragment = intent.getStringExtra("NAVIGATE_TO_FRAGMENT")
                            val productId = intent.getStringExtra("PRODUCT_ID")
                            
                            if (navigateToFragment != null) {
                                homeIntent.putExtra("NAVIGATE_TO_FRAGMENT", navigateToFragment)
                            }
                            if (!productId.isNullOrEmpty()) {
                                homeIntent.putExtra("PRODUCT_ID", productId)
                            }
                            
                            startActivity(homeIntent)
                            finish()
                        }, 2000)
                    } else {
                        val errorMessage = task.exception?.message ?: "Error en el inicio de sesiÃ³n."
                        CustomToast.showError(this, errorMessage)
                    }
                }
        }
    }
    
    /**
     * Subscribes to the low_stock topic to receive push notifications for low stock alerts.
     */
    private fun subscribeToLowStockTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("low_stock")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                } else {
                }
            }
    }
    
    /**
     * Subscribes to the pending_orders topic to receive daily push notifications for pending orders.
     */
    private fun subscribeToPendingOrdersTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("pending_orders")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                } else {
                }
            }
    }
}
