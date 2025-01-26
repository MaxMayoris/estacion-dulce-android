package com.estaciondulce.app

import RecipeSection
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityRecipeEditBinding
import com.estaciondulce.app.helpers.CategoriesHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.models.RecipeProduct
import com.google.firebase.firestore.FirebaseFirestore
import com.estaciondulce.app.helpers.SectionsHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.helpers.ProductsHelper


class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private var recipe: Recipe? = null
    // Initialize helpers
    private val sectionsHelper = SectionsHelper()
    private val recipesHelper = RecipesHelper()
    private val productsHelper = ProductsHelper()
    private val categoriesHelper = CategoriesHelper()

    private val allSections = mutableMapOf<String, String>() // Section ID to Name
    private val selectedSections = mutableListOf<RecipeSection>() // Selected Sections
    private val allProducts = mutableMapOf<String, String>() // Product ID to Name
    private val allCategories = mutableMapOf<String, String>() // Category ID to Name
    private val selectedCategories = mutableSetOf<String>() // Selected Categories

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed() // Use the dispatcher for handling back navigation
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Receta"

        // Retrieve the recipe and categories map from the intent
        recipe = intent.getParcelableExtra("recipe")
        val passedCategoriesMap = intent.getSerializableExtra("categoriesMap") as? HashMap<String, String>

        // Initialize categories map if passed
        if (passedCategoriesMap != null) {
            allCategories.putAll(passedCategoriesMap)
            setupCategorySelector()
        }

        // Fetch all necessary data before loading the UI
        fetchAllData {
            recipe = intent.getParcelableExtra("recipe")
            recipe?.let { loadRecipeData(it) } // Load the recipe after all data is ready
            binding.saveRecipeButton.setOnClickListener {
                saveRecipe()
            }
        }
    }

    private fun fetchAllData(onComplete: () -> Unit) {
        var sectionsLoaded = false
        var productsLoaded = false
        var categoriesLoaded = false

        fun checkAllDataLoaded() {
            if (sectionsLoaded && productsLoaded && categoriesLoaded) {
                onComplete() // Proceed only when all data is loaded
            }
        }

        if (allCategories.isEmpty()) {
            categoriesHelper.fetchCategories(
                onSuccess = {
                    Log.d("RecipeEditActivity", "Categories Loaded: $it")
                    allCategories.putAll(it)
                    categoriesLoaded = true
                    setupCategorySelector()
                    checkAllDataLoaded()
                },
                onError = { e ->
                    Log.e("RecipeEditActivity", "Error fetching categories: ${e.message}")
                    categoriesLoaded = true
                    checkAllDataLoaded()
                }
            )
        } else {
            categoriesLoaded = true
        }

        sectionsHelper.fetchSections(
            onSuccess = {
                Log.d("RecipeEditActivity", "Sections Loaded: $it")
                allSections.putAll(it)
                sectionsLoaded = true
                setupSectionSelector()
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeEditActivity", "Error fetching sections: ${e.message}")
                sectionsLoaded = true
                checkAllDataLoaded()
            }
        )

        productsHelper.fetchProducts(
            onSuccess = { products ->
                Log.d("RecipeEditActivity", "Products Loaded: $products")

                // Convert List<Product> to Map<String, String> for allProducts
                allProducts.putAll(products.associate { it.id to it.name })

                productsLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("RecipeEditActivity", "Error fetching products: ${e.message}")
                productsLoaded = true
                checkAllDataLoaded()
            }
        )
    }

    private fun loadRecipeData(recipe: Recipe) {
        // Set recipe details
        binding.recipeNameInput.setText(recipe.name)
        binding.recipeCostInput.setText(recipe.cost.toString())
        binding.recipeSuggestedPriceInput.setText(recipe.suggestedPrice.toString())
        binding.recipeSalePriceInput.setText(recipe.salePrice.toString())
        binding.recipeOnSaleCheckbox.isChecked = recipe.onSale

        // Clear previous data
        selectedSections.clear()
        selectedCategories.clear()

        // Populate sections and categories
        selectedSections.addAll(recipe.sections)
        selectedCategories.addAll(recipe.categories)

        Log.d("RecipeEditActivity", "Loaded Sections: $selectedSections")
        Log.d("RecipeEditActivity", "Loaded Categories: $selectedCategories")

        // Update the UI
        updateSectionsUI() // Dynamically render sections
        updateCategoryTags() // Render tags now that allCategories is populated
    }

    private fun setupCategorySelector() {
        val categoryNames = allCategories.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        binding.categorySelector.adapter = adapter

        binding.categorySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedName = adapter.getItem(position) ?: return
                val selectedId = allCategories.entries.find { it.value == selectedName }?.key ?: return

                // Check if the category is already added
                if (selectedCategories.contains(selectedId)) {
                    Toast.makeText(this@RecipeEditActivity, "La categoría ya fue añadida", Toast.LENGTH_SHORT).show()
                    return
                }
                // Add the category to the selected categories
                selectedCategories.add(selectedId)
                updateCategoryTags()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateCategoryTags() {
        binding.categoryTagsContainer.removeAllViews() // Clear existing tags

        if (selectedCategories.isEmpty()) {
            binding.categoryTagsContainer.visibility = View.GONE
            return
        }

        for (categoryId in selectedCategories) {
            val categoryName = allCategories[categoryId] ?: continue

            // Inflate and configure the tag view
            val tagView = LayoutInflater.from(this).inflate(R.layout.item_category_tag, binding.categoryTagsContainer, false)
            val textView = tagView.findViewById<TextView>(R.id.categoryTagName)
            val deleteButton = tagView.findViewById<ImageView>(R.id.categoryTagDelete)

            textView.text = categoryName

            // Handle tag removal
            deleteButton.setOnClickListener {
                selectedCategories.remove(categoryId) // Remove the category from the list
                updateCategoryTags() // Refresh tags
            }

            binding.categoryTagsContainer.addView(tagView)
        }

        binding.categoryTagsContainer.visibility = View.VISIBLE
    }



    private fun setupSectionSelector() {
        val sectionNames = allSections.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionNames)
        binding.sectionSelector.adapter = adapter

        binding.sectionSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedName = adapter.getItem(position) ?: return
                val selectedId = allSections.entries.find { it.value == selectedName }?.key ?: return

                if (selectedSections.any { it.id == selectedId }) {
                    Toast.makeText(this@RecipeEditActivity, "Esta sección ya fue añadida.", Toast.LENGTH_SHORT).show()
                    return
                }
                val newSection = RecipeSection(id = selectedId, name = selectedName, products = listOf())
                selectedSections.add(newSection)
                updateSectionsUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSectionsUI() {
        binding.sectionsContainer.removeAllViews()

        if (selectedSections.isNotEmpty()) {
            binding.sectionsTitle.visibility = View.VISIBLE
        } else {
            binding.sectionsTitle.visibility = View.GONE
        }

        for (section in selectedSections) {
            val sectionView = LayoutInflater.from(this).inflate(R.layout.item_section, binding.sectionsContainer, false)

            val sectionName = sectionView.findViewById<TextView>(R.id.sectionName)
            val searchBar = sectionView.findViewById<EditText>(R.id.productSearchBar)
            val productContainer = sectionView.findViewById<LinearLayout>(R.id.productContainer)
            val searchResultsContainer = sectionView.findViewById<LinearLayout>(R.id.searchResultsContainer)
            val removeSectionButton = sectionView.findViewById<Button>(R.id.removeSectionButton)

            sectionName.text = section.name

            // Handle product search logic
            searchBar.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchResultsContainer.removeAllViews() // Clear previous search results

                    if (s != null && s.length >= 3) { // Trigger search only after 3 characters
                        val filteredProducts = allProducts.entries.filter {
                            it.value.contains(s.toString(), ignoreCase = true)
                        }

                        for (product in filteredProducts) {
                            // Inflate a product row for each matching product
                            val productView = LayoutInflater.from(this@RecipeEditActivity)
                                .inflate(R.layout.item_product, searchResultsContainer, false)

                            val productNameView = productView.findViewById<TextView>(R.id.productName)
                            val addProductButton = productView.findViewById<Button>(R.id.addProductButton)

                            productNameView.text = product.value

                            addProductButton.setOnClickListener {
                                if (section.products.any { it.productId == product.key }) {
                                    Toast.makeText(
                                        this@RecipeEditActivity,
                                        "Este producto ya está en la sección.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@setOnClickListener
                                }

                                // Add the product to the section's list
                                val addedProductView = LayoutInflater.from(this@RecipeEditActivity)
                                    .inflate(R.layout.item_added_product, productContainer, false)

                                val addedProductName = addedProductView.findViewById<TextView>(R.id.addedProductName)
                                val productQuantity = addedProductView.findViewById<EditText>(R.id.productQuantity)
                                val removeProductButton = addedProductView.findViewById<Button>(R.id.removeProductButton)

                                addedProductName.text = product.value
                                productQuantity.setText("1")

                                removeProductButton.setOnClickListener {
                                    productContainer.removeView(addedProductView)
                                    section.products = section.products.filterNot { it.productId == product.key }
                                }

                                section.products += RecipeProduct(productId = product.key, quantity = 1.0)
                                productContainer.addView(addedProductView)

                                // Remove the product row from search results after adding
                                searchResultsContainer.removeView(productView)
                            }

                            searchResultsContainer.addView(productView) // Add the search result to the container
                        }
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Handle section removal
            removeSectionButton.setOnClickListener {
                selectedSections.remove(section)
                updateSectionsUI()
            }

            productContainer.removeAllViews()

            // Load pre-existing products for the section
            for (product in section.products) {
                val productView = LayoutInflater.from(this).inflate(R.layout.item_added_product, productContainer, false)
                val productNameView = productView.findViewById<TextView>(R.id.addedProductName)
                val productQuantity = productView.findViewById<EditText>(R.id.productQuantity)
                val removeProductButton = productView.findViewById<Button>(R.id.removeProductButton)

                productNameView.text = allProducts[product.productId] ?: "Producto desconocido"
                productQuantity.setText(product.quantity.toString())

                removeProductButton.setOnClickListener {
                    productContainer.removeView(productView)
                    section.products = section.products.filterNot { it.productId == product.productId }
                }

                productContainer.addView(productView)
            }
            binding.sectionsContainer.addView(sectionView)
        }
    }

    private fun saveRecipe() {
        validateFields { isValid ->
            if (!isValid) return@validateFields

            val updatedRecipe = Recipe(
                id = recipe?.id ?: "",
                name = binding.recipeNameInput.text.toString(),
                cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
                suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                onSale = binding.recipeOnSaleCheckbox.isChecked,
                categories = selectedCategories.toList(),
                sections = selectedSections
            )

            val db = FirebaseFirestore.getInstance()
            val recipeRef = db.collection("recipes")

            if (updatedRecipe.id.isEmpty()) {
                // New recipe: Add to Firestore
                recipeRef.add(updatedRecipe)
                    .addOnSuccessListener { documentReference ->
                        updatedRecipe.id = documentReference.id
                        sendResultAndFinish(updatedRecipe)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al guardar la receta.", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
            } else {
                // Existing recipe: Update Firestore document
                recipeRef.document(updatedRecipe.id)
                    .set(updatedRecipe)
                    .addOnSuccessListener {
                        sendResultAndFinish(updatedRecipe)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al actualizar la receta.", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
            }
        }
    }

    private fun validateFields(onValidationComplete: (Boolean) -> Unit) {
        // Check if required fields are filled
        val name = binding.recipeNameInput.text.toString().trim()
        val cost = binding.recipeCostInput.text.toString().trim()
        val suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().trim()
        val salePrice = binding.recipeSalePriceInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "El nombre de la receta es obligatorio.", Toast.LENGTH_SHORT)
                .show()
            onValidationComplete(false)
            return
        }
        if (cost.isEmpty()) {
            Toast.makeText(this, "El costo es obligatorio.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }
        if (suggestedPrice.isEmpty()) {
            Toast.makeText(this, "El precio sugerido es obligatorio.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }
        if (salePrice.isEmpty()) {
            Toast.makeText(this, "El precio de venta es obligatorio.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }

        // Check if all sections have at least one product
        for (section in selectedSections) {
            if (section.products.isEmpty()) {
                Toast.makeText(
                    this,
                    "La sección '${section.name}' debe tener al menos un producto.",
                    Toast.LENGTH_SHORT
                ).show()
                onValidationComplete(false)
                return
            }
        }

        // Check if the recipe name is unique
        val recipeNewName = binding.recipeNameInput.text.toString().trim()
        recipesHelper.isRecipeNameUnique(recipeNewName, recipe?.id) { isUnique ->
            if (!isUnique) {
                Toast.makeText(this, "Ya existe una receta con este nombre.", Toast.LENGTH_SHORT)
                    .show()
                onValidationComplete(false)
                return@isRecipeNameUnique
            }
            onValidationComplete(true)
        }
    }

    private fun sendResultAndFinish(updatedRecipe: Recipe) {
        val resultIntent = Intent().apply {
            putExtra("updatedRecipe", updatedRecipe)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
