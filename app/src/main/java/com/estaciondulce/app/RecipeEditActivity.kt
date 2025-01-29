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
import com.estaciondulce.app.helpers.SectionsHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.RecipeNested
import com.estaciondulce.app.utils.CustomLoader


class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private lateinit var loader: CustomLoader
    private var recipe: Recipe? = null

    private val sectionsHelper = SectionsHelper()
    private val recipesHelper = RecipesHelper()
    private val productsHelper = ProductsHelper()
    private val categoriesHelper = CategoriesHelper()

    private val allSections = mutableMapOf<String, String>()
    private val selectedSections = mutableListOf<RecipeSection>()
    private val allProducts = mutableMapOf<String, Pair<String, Double>>()
    private val allCategories = mutableMapOf<String, String>()
    private val selectedCategories = mutableSetOf<String>()
    private val allRecipes = mutableMapOf<String, Recipe>()
    private val selectedRecipes = mutableListOf<RecipeNested>()


    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the loader
        loader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Receta"

        // Retrieve the recipe and categories map from the intent
        recipe = intent.getParcelableExtra("recipe")
        val passedCategoriesMap =
            intent.getSerializableExtra("categoriesMap") as? HashMap<String, String>
        val passedRecipesMap = intent.getSerializableExtra("recipesMap") as? HashMap<String, Recipe>

        // Initialize recipes and categories map if passed
        if (passedRecipesMap != null) {
            allRecipes.putAll(passedRecipesMap)
            setupRecipeSearchBar()
        }
        if (passedCategoriesMap != null) {
            allCategories.putAll(passedCategoriesMap)
            setupCategorySelector()
        }

        // Fetch all necessary data before loading the UI
        loader.show()
        fetchAllData {
            recipe = intent.getParcelableExtra("recipe")
            recipe?.let { loadRecipeData(it) } // Load the recipe after all data is ready
            binding.saveRecipeButton.setOnClickListener {
                saveRecipe()
            }
            loader.hide()
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
                products.forEach { product ->
                    allProducts[product.id] =
                        Pair(product.name, product.cost) // Store name and cost
                }
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
        binding.recipeNameInput.setText(recipe.name)
        binding.recipeCostInput.setText(recipe.cost.toString())
        binding.recipeSuggestedPriceInput.setText(recipe.suggestedPrice.toString())
        binding.recipeSalePriceInput.setText(recipe.salePrice.toString())
        binding.recipeOnSaleCheckbox.isChecked = recipe.onSale

        selectedSections.clear()
        selectedCategories.clear()

        selectedSections.addAll(recipe.sections.filter {
            val hasProducts = it.products.isNotEmpty()
            Log.d("RecipeEditActivity", "Sección ${it.name} tiene productos? $hasProducts")
            hasProducts
        })

        selectedCategories.addAll(recipe.categories)

        updateSectionsUI()
        updateCategoryTags()
    }

    private fun setupRecipeSearchBar() {
        val recipeSearchAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val popupWindow = ListPopupWindow(this).apply {
            anchorView = binding.recipeSearchBar
            setAdapter(recipeSearchAdapter)
            setOnItemClickListener { _, _, position, _ ->
                val selectedRecipeName = recipeSearchAdapter.getItem(position)
                val selectedRecipe = allRecipes.values.find { it.name == selectedRecipeName }
                selectedRecipe?.let { displaySelectedRecipe(it) }
                dismiss() // Close the dropdown after selection
            }
        }

        binding.recipeSearchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 3) { // Only search when query length is at least 3
                    val filteredRecipes = allRecipes.values.filter {
                        it.name.contains(
                            query,
                            ignoreCase = true
                        ) && it.id != recipe?.id // Exclude the current recipe
                    }

                    // Update the dropdown with filtered recipes
                    recipeSearchAdapter.clear()
                    recipeSearchAdapter.addAll(filteredRecipes.map { it.name })
                    recipeSearchAdapter.notifyDataSetChanged()

                    if (filteredRecipes.isNotEmpty()) {
                        popupWindow.show() // Show the dropdown
                    } else {
                        popupWindow.dismiss() // Hide the dropdown if no results
                    }
                } else {
                    popupWindow.dismiss() // Hide the dropdown for queries shorter than 3 characters
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun displaySelectedRecipe(recipe: Recipe) {
        // Make the container visible
        binding.selectedRecipeContainer.visibility = View.VISIBLE

        // Check if the recipe already exists in the UI
        val existingRow = binding.selectedRecipeContainer.findViewWithTag<LinearLayout>(recipe.id)
        if (existingRow != null) {
            Toast.makeText(this, "Esta receta ya fue añadida.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a new row for the recipe
        val recipeRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            tag = recipe.id // Set tag to identify this row later
        }

        // Recipe Name (non-editable)
        val nameView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            text = recipe.name
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.black))
        }

        // Recipe Cost (non-editable)
        val costView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = recipe.cost.toString()
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.black))
        }

        // Quantity Input (editable)
        val quantityInput = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Cantidad"
            setPadding(8, 8, 8, 8)
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("1") // Default quantity
        }

        // Delete Button
        val deleteButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "X"
            setOnClickListener {
                binding.selectedRecipeContainer.removeView(recipeRow) // Remove the row
                selectedRecipes.removeIf { it.recipeId == recipe.id } // Remove from the list
            }
        }

        // Add views to the row
        recipeRow.addView(nameView)
        recipeRow.addView(costView)
        recipeRow.addView(quantityInput)
        recipeRow.addView(deleteButton)

        // Add the row to the container
        binding.selectedRecipeContainer.addView(recipeRow)

        // Add or update the recipe in the selectedRecipes list
        quantityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val quantity = s.toString().toDoubleOrNull() ?: 1.0
                val existingRecipe = selectedRecipes.find { it.recipeId == recipe.id }
                if (existingRecipe != null) {
                    existingRecipe.quantity = quantity
                } else {
                    selectedRecipes.add(RecipeNested(recipeId = recipe.id, quantity = quantity))
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCategorySelector() {
        val categoryNames = allCategories.values.toList()
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        binding.categorySelector.adapter = adapter

        binding.categorySelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedName = adapter.getItem(position) ?: return
                    val selectedId =
                        allCategories.entries.find { it.value == selectedName }?.key ?: return

                    // Check if the category is already added
                    if (selectedCategories.contains(selectedId)) {
                        Toast.makeText(
                            this@RecipeEditActivity,
                            "La categoría ya fue añadida",
                            Toast.LENGTH_SHORT
                        ).show()
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
            val tagView = LayoutInflater.from(this)
                .inflate(R.layout.item_category_tag, binding.categoryTagsContainer, false)
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
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionNames)
        binding.sectionSelector.adapter = adapter

        binding.sectionSelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedName = adapter.getItem(position) ?: return
                    val selectedId =
                        allSections.entries.find { it.value == selectedName }?.key ?: return

                    if (selectedSections.any { it.id == selectedId }) {
                        Toast.makeText(
                            this@RecipeEditActivity,
                            "Esta sección ya fue añadida.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    val newSection =
                        RecipeSection(id = selectedId, name = selectedName, products = listOf())
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
            val sectionView = LayoutInflater.from(this)
                .inflate(R.layout.item_section_recipe_edit, binding.sectionsContainer, false)
            val sectionName = sectionView.findViewById<TextView>(R.id.sectionName)
            val productSearchBar = sectionView.findViewById<EditText>(R.id.productSearchBar)
            val productContainer = sectionView.findViewById<LinearLayout>(R.id.productContainer)
            val removeSectionButton = sectionView.findViewById<Button>(R.id.removeSectionButton)

            sectionView.tag = section.id
            sectionName.text = section.name

            productSearchBar.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.length >= 3) {
                        val filteredProducts = allProducts.entries.filter {
                            it.value.first.contains(
                                query,
                                ignoreCase = true
                            )
                        }
                        showProductSearchPopup(productSearchBar, filteredProducts, section)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            removeSectionButton.setOnClickListener {
                selectedSections.remove(section)
                updateSectionsUI()
            }

            productContainer.removeAllViews()
            for (product in section.products) {
                val productEntry = allProducts[product.productId]
                if (productEntry != null) {
                    addProductToUI(
                        productContainer,
                        section,
                        product.productId,
                        productEntry.first,
                        productEntry.second,
                        product.quantity
                    )
                } else {
                    addProductToUI(
                        productContainer,
                        section,
                        product.productId,
                        "Producto desconocido",
                        0.0,
                        product.quantity
                    )
                }
            }
            binding.sectionsContainer.addView(sectionView)
        }
    }

    private fun showProductSearchPopup(
        anchor: View,
        products: List<Map.Entry<String, Pair<String, Double>>>,
        section: RecipeSection
    ) {
        val productSearchAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, products.map { it.value.first })
        val popupWindow = ListPopupWindow(this).apply {
            setAnchorView(anchor)
            setAdapter(productSearchAdapter)
            setOnItemClickListener { _, _, position, _ ->
                val selectedProduct = products[position]
                section.products += RecipeProduct(selectedProduct.key, 1.0)
                updateSectionsUI()
                dismiss()
            }
        }
        popupWindow.show()
    }

    private fun addProductToUI(
        productContainer: LinearLayout,
        section: RecipeSection,
        productId: String,
        productName: String,
        productCost: Double,
        quantity: Double
    ) {
        val productView = LayoutInflater.from(this)
            .inflate(R.layout.item_added_product_recipe_edit, productContainer, false)
        val productNameView = productView.findViewById<TextView>(R.id.addedProductName)
        val productCostView = productView.findViewById<TextView>(R.id.addedProductCost)
        val productQuantityInput = productView.findViewById<EditText>(R.id.productQuantity)
        val removeProductButton = productView.findViewById<Button>(R.id.removeProductButton)

        productNameView.text = productName
        productCostView.text = productCost.toString()
        productQuantityInput.setText(quantity.toString())

        productQuantityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val updatedQuantity = s.toString().toDoubleOrNull() ?: 1.0
                section.products.find { it.productId == productId }?.quantity = updatedQuantity
                Log.d(
                    "RecipeEditActivity",
                    "Cantidad actualizada para $productName: $updatedQuantity"
                )
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        removeProductButton.setOnClickListener {
            productContainer.removeView(productView)
            section.products = section.products.filterNot { it.productId == productId }
            updateSectionsUI()
        }

        productContainer.addView(productView)
    }


    private fun saveRecipe() {
        validateFields { isValid ->
            if (!isValid) return@validateFields

            val updatedRecipe = Recipe(
                id = recipe?.id ?: "",
                name = binding.recipeNameInput.text.toString(),
                cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
                suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull()
                    ?: 0.0,
                salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                onSale = binding.recipeOnSaleCheckbox.isChecked,
                categories = selectedCategories.toList(),
                sections = selectedSections,
                recipes = selectedRecipes
            )

            if (updatedRecipe.id.isEmpty()) {
                recipesHelper.addRecipe(
                    recipe = updatedRecipe,
                    onSuccess = { newRecipe ->
                        sendResultAndFinish(newRecipe)
                    },
                    onError = { e ->
                        Toast.makeText(this, "Error al guardar la receta.", Toast.LENGTH_SHORT)
                            .show()
                        e.printStackTrace()
                    }
                )
            } else {
                recipesHelper.updateRecipe(
                    recipeId = updatedRecipe.id,
                    recipe = updatedRecipe,
                    onSuccess = {
                        sendResultAndFinish(updatedRecipe)
                    },
                    onError = { e ->
                        Toast.makeText(this, "Error al actualizar la receta.", Toast.LENGTH_SHORT)
                            .show()
                        e.printStackTrace()
                    }
                )
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
