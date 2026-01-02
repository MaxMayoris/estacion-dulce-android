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
        
        val navigateToFragmentOnCreate = intent.getStringExtra("NAVIGATE_TO_FRAGMENT")
            ?: intent.getStringExtra("screen")
        val productIdOnCreate = intent.getStringExtra("PRODUCT_ID")
            ?: intent.getStringExtra("productId")

        if (skipLoginForDebug) {
            val homeIntent = Intent(this, HomeActivity::class.java)
            
            if (navigateToFragmentOnCreate != null) {
                homeIntent.putExtra("NAVIGATE_TO_FRAGMENT", navigateToFragmentOnCreate)
            }
            if (!productIdOnCreate.isNullOrEmpty()) {
                homeIntent.putExtra("PRODUCT_ID", productIdOnCreate)
            }
            
            startActivity(homeIntent)
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
                                ?: intent.getStringExtra("screen")
                            val productId = intent.getStringExtra("PRODUCT_ID")
                                ?: intent.getStringExtra("productId")
                            
                            
                            if (navigateToFragment != null) {
                                homeIntent.putExtra("NAVIGATE_TO_FRAGMENT", navigateToFragment)
                                homeIntent.putExtra("screen", navigateToFragment)
                            }
                            if (!productId.isNullOrEmpty()) {
                                homeIntent.putExtra("PRODUCT_ID", productId)
                                homeIntent.putExtra("productId", productId)
                            }
                            
                            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(homeIntent)
                            finish()
                        }, 2000)
                    } else {
                        val errorMessage = task.exception?.message ?: "Error en el inicio de sesiÃƒÂ³n."
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
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
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
