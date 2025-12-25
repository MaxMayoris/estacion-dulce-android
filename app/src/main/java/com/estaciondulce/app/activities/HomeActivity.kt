package com.estaciondulce.app.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.estaciondulce.app.R
import com.estaciondulce.app.fragments.MovementFragment
import com.estaciondulce.app.fragments.PersonFragment
import com.estaciondulce.app.fragments.ProductFragment
import com.estaciondulce.app.fragments.RecipeFragment
import com.estaciondulce.app.fragments.ShipmentFragment
import com.estaciondulce.app.fragments.KitchenOrderFragment
import com.estaciondulce.app.fragments.StatisticsFragment
import com.estaciondulce.app.fragments.ChatFragment
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var loader: CustomLoader
    private lateinit var auth: FirebaseAuth
    private var hasNavigatedFromNotification = false
    
    private val productFragment = ProductFragment()
    private val recipeFragment = RecipeFragment()
    private val personFragment = PersonFragment()
    private val movementFragment = MovementFragment()
    private val shipmentFragment = ShipmentFragment()
    private val statisticsFragment = StatisticsFragment()
    private val chatFragment = ChatFragment()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
        } else {
        }
    }

    /**
     * Initializes the activity, sets up Firestore listeners,
     * observes global LiveData, and configures the dashboard cards.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        
        if (auth.currentUser == null) {
            val loginIntent = Intent(this, LoginActivity::class.java)
            val navigateToFragment = intent.getStringExtra("NAVIGATE_TO_FRAGMENT")
            val productId = intent.getStringExtra("PRODUCT_ID")
            
            if (navigateToFragment != null) {
                loginIntent.putExtra("NAVIGATE_TO_FRAGMENT", navigateToFragment)
            }
            if (!productId.isNullOrEmpty()) {
                loginIntent.putExtra("PRODUCT_ID", productId)
            }
            
            startActivity(loginIntent)
            finish()
            return
        }
        
        setTheme(R.style.Theme_EstacionDulceApp_Home)
        setContentView(R.layout.activity_home)
        
        loader = CustomLoader(this)
        loader.show()

        FirestoreRepository.startListeners()

        val dataLoadedObserver = androidx.lifecycle.Observer<Any> { 
            checkDataLoaded()
        }
        FirestoreRepository.recipesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.productsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.measuresLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.categoriesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.sectionsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.personsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.movementsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.shipmentSettingsLiveData.observe(this) { checkDataLoaded() }

        setupDashboardCards()
        setupLogoutButton()
        setupFragmentHeader()
        setupChatFab()
        requestNotificationPermission()
        showDashboard()
    }
    
    /**
     * Handles navigation to fragment from push notification.
     * Waits for data to be loaded before navigating.
     */
    private fun handleNotificationNavigation() {
        val navigateToFragment = intent.getStringExtra("NAVIGATE_TO_FRAGMENT")
        val productId = intent.getStringExtra("PRODUCT_ID")
        
        if (navigateToFragment != null) {
            when (navigateToFragment) {
                "kitchen_orders" -> {
                    loadFragment(KitchenOrderFragment(), "Pedidos")
                    showFragmentContainer()
                }
                "product_detail" -> {
                    if (!productId.isNullOrEmpty()) {
                        val intent = Intent(this, ProductEditActivity::class.java)
                        intent.putExtra("PRODUCT_ID", productId)
                        startActivity(intent)
                    }
                }
                "home" -> {
                    showDashboard()
                }
            }
            intent.removeExtra("NAVIGATE_TO_FRAGMENT")
            intent.removeExtra("PRODUCT_ID")
        }
    }
    
    /**
     * Requests notification permission for Android 13+ (API 33+).
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * Sets up click listeners for all dashboard cards.
     */
    private fun setupDashboardCards() {
        findViewById<MaterialCardView>(R.id.productsCard).setOnClickListener {
            loadFragment(productFragment, "Productos")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.recipesCard).setOnClickListener {
            loadFragment(recipeFragment, "Recetas")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.personsCard).setOnClickListener {
            loadFragment(personFragment, "Personas")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.movementsCard).setOnClickListener {
            loadFragment(movementFragment, "Movimientos")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.kitchenOrdersCard).setOnClickListener {
            loadFragment(KitchenOrderFragment(), "Pedidos")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.shipmentsCard).setOnClickListener {
            loadFragment(shipmentFragment, "Envios")
            showFragmentContainer()
        }

        findViewById<MaterialCardView>(R.id.statisticsCard).setOnClickListener {
            loadFragment(statisticsFragment, "Estadísticas")
            showFragmentContainer()
        }
    }

    /**
     * Sets up the logout button functionality.
     */
    private fun setupLogoutButton() {
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            loader.show()
            auth.signOut()
            loader.hide()
            CustomToast.showSuccess(this, getString(R.string.logout_success))
            
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    /**
     * Sets up the fragment header with back button functionality.
     */
    private fun setupFragmentHeader() {
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            showDashboard()
        }
    }

    /**
     * Sets up the chat floating action button functionality.
     */
    private fun setupChatFab() {
        findViewById<FloatingActionButton>(R.id.chatFab).setOnClickListener {
            loadFragment(chatFragment, "Chat con Cha")
            showFragmentContainer()
        }
    }

    /**
     * Shows the dashboard cards and hides the fragment container and header.
     */
    private fun showDashboard() {
        findViewById<View>(R.id.dashboardScrollView).visibility = View.VISIBLE
        findViewById<View>(R.id.fragmentHeader).visibility = View.GONE
        findViewById<View>(R.id.homeFragmentContainer).visibility = View.GONE
        findViewById<FloatingActionButton>(R.id.chatFab).visibility = View.VISIBLE
    }

    /**
     * Shows the fragment container and header, hides the dashboard cards.
     */
    private fun showFragmentContainer() {
        findViewById<View>(R.id.dashboardScrollView).visibility = View.GONE
        findViewById<View>(R.id.fragmentHeader).visibility = View.VISIBLE
        findViewById<View>(R.id.homeFragmentContainer).visibility = View.VISIBLE
    }

    /**
     * Loads the specified fragment into the home container and sets the title.
     */
    private fun loadFragment(fragment: Fragment, title: String) {
        findViewById<TextView>(R.id.fragmentTitle).text = title
        
        val transaction = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.fragments.contains(fragment)) {
            supportFragmentManager.fragments.forEach { transaction.hide(it) }
            transaction.show(fragment)
        } else {
            supportFragmentManager.fragments.forEach { transaction.hide(it) }
            transaction.add(R.id.homeFragmentContainer, fragment)
        }
        transaction.commit()
        
        findViewById<FloatingActionButton>(R.id.chatFab).visibility = View.GONE
    }

    /**
     * Checks if data are loaded and hides the loader when loading is complete (success or failure).
     */
    private fun checkDataLoaded() {
        val recipesLoaded = FirestoreRepository.recipesLiveData.value != null
        val productsLoaded = FirestoreRepository.productsLiveData.value != null
        val measuresLoaded = FirestoreRepository.measuresLiveData.value != null
        val categoriesLoaded = FirestoreRepository.categoriesLiveData.value != null
        val sectionsLoaded = FirestoreRepository.sectionsLiveData.value != null
        val personsLoaded = FirestoreRepository.personsLiveData.value != null
        val movementsLoaded = FirestoreRepository.movementsLiveData.value != null
        val shipmentSettingsLoaded = FirestoreRepository.shipmentSettingsLiveData.value != null

        if (recipesLoaded && productsLoaded && measuresLoaded && categoriesLoaded && sectionsLoaded && personsLoaded && movementsLoaded && shipmentSettingsLoaded) {
            loader.hide()
            if (!hasNavigatedFromNotification) {
                handleNotificationNavigation()
                hasNavigatedFromNotification = true
            }
        }
    }

    /**
     * Handles back button press to return to dashboard.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (findViewById<View>(R.id.homeFragmentContainer).visibility == View.VISIBLE) {
            showDashboard()
        } else {
            super.onBackPressed()
        }
    }
    
}
