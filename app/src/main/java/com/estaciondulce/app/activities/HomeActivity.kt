package com.estaciondulce.app.activities

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.R
import com.estaciondulce.app.fragments.TransactionFragment
import com.estaciondulce.app.fragments.HomeFragment
import com.estaciondulce.app.fragments.PersonFragment
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

    /**
     * Initializes the activity, sets up Firestore listeners,
     * observes global LiveData, and configures the bottom navigation.
     */
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        loader = CustomLoader(this)
        loader.show()

        FirestoreRepository.startListeners()

        val dataLoadedObserver = Observer<Any> { checkDataLoaded() }
        FirestoreRepository.recipesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.productsLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.measuresLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.categoriesLiveData.observe(this, dataLoadedObserver)
        FirestoreRepository.sectionsLiveData.observe(this, dataLoadedObserver)

        val bottomNavigationView =
            findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        loadFragment(homeFragment)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment); true
                }

                R.id.nav_product -> {
                    loadFragment(productFragment); true
                }

                R.id.nav_recipe -> {
                    loadFragment(recipeFragment); true
                }

                R.id.nav_person -> {
                    loadFragment(personFragment); true
                }

                R.id.nav_transaction -> {
                    loadFragment(transactionFragment); true
                }

                else -> false
            }
        }
    }

    /**
     * Checks if recipes, products, and measures are loaded and hides the loader if so.
     */
    private fun checkDataLoaded() {
        val recipesLoaded = FirestoreRepository.recipesLiveData.value?.isNotEmpty() ?: false
        val productsLoaded = FirestoreRepository.productsLiveData.value?.isNotEmpty() ?: false
        val measuresLoaded = FirestoreRepository.measuresLiveData.value?.isNotEmpty() ?: false
        val categoriesLoaded = FirestoreRepository.categoriesLiveData.value?.isNotEmpty() ?: false
        val sectionsLoaded = FirestoreRepository.sectionsLiveData.value?.isNotEmpty() ?: false

        if (recipesLoaded && productsLoaded && measuresLoaded && categoriesLoaded && sectionsLoaded) {
            loader.hide()
        }
    }

    /**
     * Loads the specified fragment into the home container.
     */
    private fun loadFragment(fragment: Fragment) {
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
}
