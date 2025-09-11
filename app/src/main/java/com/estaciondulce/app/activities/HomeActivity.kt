package com.estaciondulce.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.estaciondulce.app.R
import com.estaciondulce.app.fragments.MovementFragment
import com.estaciondulce.app.fragments.PersonFragment
import com.estaciondulce.app.fragments.ProductFragment
import com.estaciondulce.app.fragments.RecipeFragment
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var loader: CustomLoader
    private lateinit var auth: FirebaseAuth
    
    private val productFragment = ProductFragment()
    private val recipeFragment = RecipeFragment()
    private val personFragment = PersonFragment()
    private val movementFragment = MovementFragment()

    /**
     * Initializes the activity, sets up Firestore listeners,
     * observes global LiveData, and configures the dashboard cards.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_EstacionDulceApp_Home)
        setContentView(R.layout.activity_home)
        
        loader = CustomLoader(this)
        auth = FirebaseAuth.getInstance()
        loader.show()

        FirestoreRepository.startListeners()

        val dataLoadedObserver = androidx.lifecycle.Observer<Any> { checkDataLoaded() }
        FirestoreRepository.recipesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.productsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.measuresLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.categoriesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.sectionsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.personsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.movementsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.addressesLiveData.observe(this, dataLoadedObserver)

        setupDashboardCards()
        setupLogoutButton()
        setupFragmentHeader()
        showDashboard()
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
    }

    /**
     * Sets up the logout button functionality.
     */
    private fun setupLogoutButton() {
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            loader.show()
            auth.signOut()
            loader.hide()
            CustomToast.showSuccess(this, "Sesión cerrada correctamente.")
            
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
     * Shows the dashboard cards and hides the fragment container and header.
     */
    private fun showDashboard() {
        findViewById<View>(R.id.dashboardScrollView).visibility = View.VISIBLE
        findViewById<View>(R.id.fragmentHeader).visibility = View.GONE
        findViewById<View>(R.id.homeFragmentContainer).visibility = View.GONE
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
    }

    /**
     * Checks if data are loaded and hides the loader if so.
     */
    private fun checkDataLoaded() {
        val recipesLoaded = FirestoreRepository.recipesLiveData.value?.isNotEmpty() ?: false
        val productsLoaded = FirestoreRepository.productsLiveData.value?.isNotEmpty() ?: false
        val measuresLoaded = FirestoreRepository.measuresLiveData.value?.isNotEmpty() ?: false
        val categoriesLoaded = FirestoreRepository.categoriesLiveData.value?.isNotEmpty() ?: false
        val sectionsLoaded = FirestoreRepository.sectionsLiveData.value?.isNotEmpty() ?: false
        val personsLoaded = FirestoreRepository.personsLiveData.value?.isNotEmpty() ?: false
        val movementsLoaded = FirestoreRepository.movementsLiveData.value?.isNotEmpty() ?: false
        val addressesLoaded = FirestoreRepository.addressesLiveData.value?.isNotEmpty() ?: false

        if (recipesLoaded && productsLoaded && measuresLoaded && categoriesLoaded && sectionsLoaded && personsLoaded && movementsLoaded && addressesLoaded) {
            loader.hide()
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
