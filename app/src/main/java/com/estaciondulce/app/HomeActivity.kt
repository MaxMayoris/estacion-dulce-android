package com.estaciondulce.app

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.fragments.ProductFragment
import com.estaciondulce.app.fragments.RecipeFragment
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val productFragment = ProductFragment()
    private val recipeFragment = RecipeFragment()
    private val personFragment = PersonFragment()
    private val transactionFragment = TransactionFragment()
    private lateinit var loader: CustomLoader

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        loader = CustomLoader(this)
        loader.show()

        // Start listening to Firestore changes when the app loads.
        FirestoreRepository.startListeners()

        // Observe the global LiveData objects for recipes, products, and measures.
        val dataLoadedObserver = Observer<Any> {
            checkDataLoaded()
        }
        FirestoreRepository.recipesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.productsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.measuresLiveData.observe(this, dataLoadedObserver)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Set the default fragment
        loadFragment(homeFragment)

        // Handle navigation item clicks
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                    true
                }

                R.id.nav_product -> {
                    loadFragment(productFragment)
                    true
                }

                R.id.nav_recipe -> {
                    loadFragment(recipeFragment)
                    true
                }

                R.id.nav_person -> {
                    loadFragment(personFragment)
                    true
                }

                R.id.nav_transaction -> {
                    loadFragment(transactionFragment)
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Checks if all required data (recipes, products, measures) is loaded.
     * Hides the loader when all data is available.
     */
    private fun checkDataLoaded() {
        val recipesLoaded = FirestoreRepository.recipesLiveData.value?.isNotEmpty() ?: false
        val productsLoaded = FirestoreRepository.productsLiveData.value?.isNotEmpty() ?: false
        val measuresLoaded = FirestoreRepository.measuresLiveData.value?.isNotEmpty() ?: false

        if (recipesLoaded && productsLoaded && measuresLoaded) {
            loader.hide()
        }
    }

    // Function to load fragments into the container
    private fun loadFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        // Check if the fragment is already added
        if (supportFragmentManager.fragments.contains(fragment)) {
            // Show the fragment if already added
            supportFragmentManager.fragments.forEach { fragmentTransaction.hide(it) }
            fragmentTransaction.show(fragment)
        } else {
            // Add and show the fragment
            supportFragmentManager.fragments.forEach { fragmentTransaction.hide(it) }
            fragmentTransaction.add(R.id.homeFragmentContainer, fragment)
        }

        fragmentTransaction.commit()
    }
}
