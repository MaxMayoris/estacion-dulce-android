package com.estaciondulce.app

import RecipeSection
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.estaciondulce.app.databinding.ActivityRecipeEditBinding
import com.estaciondulce.app.helpers.CategoriesHelper
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.helpers.SectionsHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.models.RecipeNested
import com.estaciondulce.app.models.RecipeProduct
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

    private var isSectionSpinnerInitialized = false

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Receta"

        recipe = intent.getParcelableExtra("recipe")
        val passedCategoriesMap = intent.getSerializableExtra("categoriesMap") as? HashMap<String, String>
        val passedRecipesMap = intent.getSerializableExtra("recipesMap") as? HashMap<String, Recipe>

        passedRecipesMap?.let {
            allRecipes.putAll(it)
            setupRecipeSearchBar()
        }
        passedCategoriesMap?.let {
            allCategories.putAll(it)
            setupCategorySelector()
        }

        loader.show()
        fetchAllData {
            recipe = intent.getParcelableExtra("recipe")
            recipe?.let { loadRecipeData(it) }
            binding.saveRecipeButton.setOnClickListener { saveRecipe() }
            loader.hide()
        }
    }

    private fun fetchAllData(onComplete: () -> Unit) {
        var sectionsLoaded = false
        var productsLoaded = false
        var categoriesLoaded = false

        fun checkAllDataLoaded() {
            if (sectionsLoaded && productsLoaded && categoriesLoaded) {
                onComplete()
            }
        }

        if (allCategories.isEmpty()) {
            categoriesHelper.fetchCategories(
                onSuccess = {
                    allCategories.putAll(it)
                    categoriesLoaded = true
                    setupCategorySelector()
                    checkAllDataLoaded()
                },
                onError = {
                    categoriesLoaded = true
                    checkAllDataLoaded()
                }
            )
        } else {
            categoriesLoaded = true
        }

        sectionsHelper.fetchSections(
            onSuccess = {
                allSections.putAll(it)
                sectionsLoaded = true
                setupSectionSelector()
                checkAllDataLoaded()
            },
            onError = {
                sectionsLoaded = true
                checkAllDataLoaded()
            }
        )

        productsHelper.fetchProducts(
            onSuccess = { products ->
                products.forEach { product ->
                    allProducts[product.id] = Pair(product.name, product.cost)
                }
                productsLoaded = true
                checkAllDataLoaded()
            },
            onError = {
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
        binding.recipeUnitInput.setText(recipe.unit.toString())

        selectedSections.clear()
        selectedCategories.clear()
        selectedSections.addAll(recipe.sections.filter { it.products.isNotEmpty() })
        selectedCategories.addAll(recipe.categories)
        selectedRecipes.clear()
        selectedRecipes.addAll(recipe.recipes)

        updateSectionsUI()
        updateCategoryTags()
        updateNestedRecipesUI()
        updateCosts()
    }

    private fun updateCosts() {
        val units = binding.recipeUnitInput.text.toString().toDoubleOrNull() ?: 1.0
        val updatedRecipe = Recipe(
            id = recipe?.id ?: "",
            name = binding.recipeNameInput.text.toString(),
            cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
            suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            unit = units,
            onSale = binding.recipeOnSaleCheckbox.isChecked,
            categories = selectedCategories.toList(),
            sections = selectedSections,
            recipes = selectedRecipes
        )
        val (costPerUnit, suggestedPrice) = recipesHelper.calculateCostAndSuggestedPrice(
            updatedRecipe,
            allProducts,
            allRecipes
        )
        binding.recipeCostInput.setText(String.format("%.2f", costPerUnit))
        binding.recipeSuggestedPriceInput.setText(String.format("%.2f", suggestedPrice))
    }

    private fun updateNestedRecipesUI() {
        binding.selectedRecipeContainer.removeAllViews()
        binding.selectedRecipeContainer.visibility =
            if (selectedRecipes.isNotEmpty()) View.VISIBLE else View.GONE

        for (nested in selectedRecipes) {
            val recipeData = allRecipes[nested.recipeId]
            if (recipeData != null) {
                val recipeRow = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 8, 8, 8)
                    tag = recipeData.id
                }

                val nameView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    text = recipeData.name
                    setPadding(8, 8, 8, 8)
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                }

                val costView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = String.format("%.2f", recipeData.cost)
                    setPadding(8, 8, 8, 8)
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                }

                val quantityControl = createQuantityControl(nested.quantity) { newQuantity ->
                    nested.quantity = newQuantity
                    updateCosts()
                }

                val scale = resources.displayMetrics.density
                val deleteSize = (30 * scale + 0.5f).toInt()
                val deleteButton = ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(deleteSize, deleteSize).apply {
                        setMargins(8, 0, 0, 0)
                    }
                    setBackgroundColor(ContextCompat.getColor(this@RecipeEditActivity, R.color.purple_700))
                    setImageDrawable(ContextCompat.getDrawable(this@RecipeEditActivity, R.drawable.ic_delete))
                    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@RecipeEditActivity, android.R.color.white))
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    adjustViewBounds = true
                    setPadding(0, 0, 0, 0)
                }
                deleteButton.setOnClickListener {
                    showConfirmationDialog("¿Está seguro de eliminar la receta ${recipeData.name}?") {
                        binding.selectedRecipeContainer.removeView(recipeRow)
                        selectedRecipes.removeIf { it.recipeId == recipeData.id }
                        updateCosts()
                    }
                }

                recipeRow.addView(nameView)
                recipeRow.addView(costView)
                recipeRow.addView(quantityControl)
                recipeRow.addView(deleteButton)
                binding.selectedRecipeContainer.addView(recipeRow)
            }
        }
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
                dismiss()
            }
        }
        binding.recipeSearchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 3) {
                    val filteredRecipes = allRecipes.values.filter {
                        it.name.contains(query, ignoreCase = true) && it.id != recipe?.id
                    }
                    recipeSearchAdapter.clear()
                    recipeSearchAdapter.addAll(filteredRecipes.map { it.name })
                    recipeSearchAdapter.notifyDataSetChanged()
                    if (filteredRecipes.isNotEmpty()) {
                        popupWindow.show()
                    } else {
                        popupWindow.dismiss()
                    }
                } else {
                    popupWindow.dismiss()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun displaySelectedRecipe(recipe: Recipe) {
        binding.selectedRecipeContainer.visibility = View.VISIBLE

        val existingRow = binding.selectedRecipeContainer.findViewWithTag<LinearLayout>(recipe.id)
        if (existingRow != null) {
            Toast.makeText(this, "Esta receta ya fue añadida.", Toast.LENGTH_SHORT).show()
            return
        }
        val recipeRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            tag = recipe.id
        }
        val nameView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            text = recipe.name
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        val costView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = recipe.cost.toString()
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        val quantityControl = createQuantityControl(1.0) { newQuantity ->
            val nested = selectedRecipes.find { it.recipeId == recipe.id } ?: RecipeNested(recipeId = recipe.id, quantity = 1.0)
            nested.quantity = newQuantity
            if (!selectedRecipes.contains(nested)) {
                selectedRecipes.add(nested)
            }
            updateCosts()
        }
        val scale = resources.displayMetrics.density
        val deleteSize = (30 * scale + 0.5f).toInt()
        val deleteButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(deleteSize, deleteSize).apply {
                setMargins(8, 0, 0, 0)
            }
            setBackgroundColor(ContextCompat.getColor(this@RecipeEditActivity, R.color.purple_700))
            setImageDrawable(ContextCompat.getDrawable(this@RecipeEditActivity, R.drawable.ic_delete))
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@RecipeEditActivity, android.R.color.white))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(0, 0, 0, 0)
        }
        deleteButton.setOnClickListener {
            showConfirmationDialog("¿Está seguro de eliminar la receta ${recipe.name}?") {
                binding.selectedRecipeContainer.removeView(recipeRow)
                selectedRecipes.removeIf { it.recipeId == recipe.id }
                updateCosts()
            }
        }
        recipeRow.addView(nameView)
        recipeRow.addView(costView)
        recipeRow.addView(quantityControl)
        recipeRow.addView(deleteButton)
        binding.selectedRecipeContainer.addView(recipeRow)
        if (selectedRecipes.none { it.recipeId == recipe.id }) {
            val newRecipeNested = RecipeNested(recipeId = recipe.id, quantity = 1.0)
            selectedRecipes.add(newRecipeNested)
        }
        updateCosts()
        binding.recipeSearchBar.text.clear()
    }

    private fun setupCategorySelector() {
        val categoryNames = allCategories.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        binding.categorySelector.adapter = adapter

        binding.categorySelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedName = adapter.getItem(position) ?: return
                    val selectedId = allCategories.entries.find { it.value == selectedName }?.key ?: return
                    if (selectedCategories.contains(selectedId)) {
                        Toast.makeText(this@RecipeEditActivity, "La categoría ya fue añadida", Toast.LENGTH_SHORT).show()
                        return
                    }
                    selectedCategories.add(selectedId)
                    updateCategoryTags()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun updateCategoryTags() {
        binding.categoryTagsContainer.removeAllViews()
        if (selectedCategories.isEmpty()) {
            binding.categoryTagsContainer.visibility = View.GONE
            return
        }
        for (categoryId in selectedCategories) {
            val categoryName = allCategories[categoryId] ?: continue
            val tagView = LayoutInflater.from(this).inflate(R.layout.item_category_tag, binding.categoryTagsContainer, false)
            val textView = tagView.findViewById<TextView>(R.id.categoryTagName)
            val deleteButton = tagView.findViewById<ImageView>(R.id.categoryTagDelete)
            textView.text = categoryName
            deleteButton.setOnClickListener {
                selectedCategories.remove(categoryId)
                updateCategoryTags()
            }
            binding.categoryTagsContainer.addView(tagView)
        }
        binding.categoryTagsContainer.visibility = View.VISIBLE
    }

    private fun setupSectionSelector() {
        val sectionNames = allSections.values.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionNames)
        binding.sectionSelector.adapter = adapter

        binding.sectionSelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!isSectionSpinnerInitialized) {
                        isSectionSpinnerInitialized = true
                        return
                    }
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
        binding.sectionsTitle.visibility = if (selectedSections.isNotEmpty()) View.VISIBLE else View.GONE

        for (section in selectedSections) {
            val sectionView = LayoutInflater.from(this).inflate(R.layout.item_section_recipe_edit, binding.sectionsContainer, false)
            val sectionName = sectionView.findViewById<TextView>(R.id.sectionName)
            val productSearchBar = sectionView.findViewById<EditText>(R.id.productSearchBar)
            val productContainer = sectionView.findViewById<LinearLayout>(R.id.productContainer)
            val removeSectionButton = sectionView.findViewById<Button>(R.id.removeSectionButton)

            sectionView.tag = section.id
            sectionName.text = section.name

            productSearchBar.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.length >= 3) {
                        val filteredProducts = allProducts.entries.filter {
                            it.value.first.contains(query, ignoreCase = true)
                        }
                        showProductSearchPopup(productSearchBar, filteredProducts, section)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            removeSectionButton.setOnClickListener {
                showConfirmationDialog("¿Está seguro de eliminar la sección ${section.name}?") {
                    selectedSections.remove(section)
                    updateSectionsUI()
                    updateCosts()
                }
            }

            productContainer.removeAllViews()
            for (product in section.products) {
                val productEntry = allProducts[product.productId]
                if (productEntry != null) {
                    addProductToUI(productContainer, section, product, productEntry.first, productEntry.second)
                } else {
                    addProductToUI(productContainer, section, product, "Producto desconocido", 0.0)
                }
            }
            binding.sectionsContainer.addView(sectionView)
        }
    }

    private fun showProductSearchPopup(anchor: View, products: List<Map.Entry<String, Pair<String, Double>>>, section: RecipeSection) {
        val productSearchAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, products.map { it.value.first })
        val popupWindow = ListPopupWindow(this).apply {
            anchorView = anchor
            setAdapter(productSearchAdapter)
            setOnItemClickListener { _, _, position, _ ->
                val selectedProduct = products[position]
                if (section.products.any { it.productId == selectedProduct.key }) {
                    Toast.makeText(this@RecipeEditActivity, "El producto ya fue añadido en esta sección.", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@setOnItemClickListener
                }
                section.products += RecipeProduct(selectedProduct.key, 1.0)
                if (anchor is EditText) {
                    anchor.text.clear()
                }
                updateSectionsUI()
                updateCosts()
                dismiss()
            }
        }
        popupWindow.show()
    }

    private fun addProductToUI(productContainer: LinearLayout, section: RecipeSection, recipeProduct: RecipeProduct, productName: String, productCost: Double) {
        val productView = LayoutInflater.from(this).inflate(R.layout.item_added_product_recipe_edit, productContainer, false)
        val productNameView = productView.findViewById<TextView>(R.id.addedProductName)
        val productCostView = productView.findViewById<TextView>(R.id.addedProductCost)
        val quantityContainer = productView.findViewById<LinearLayout>(R.id.productQuantity)
        val removeProductButton = productView.findViewById<ImageButton>(R.id.removeProductButton)

        productNameView.text = productName
        productCostView.text = productCost.toString()

        val quantityControl = createQuantityControl(recipeProduct.quantity) { newQuantity ->
            recipeProduct.quantity = newQuantity
            updateCosts()
        }
        quantityContainer.removeAllViews()
        quantityContainer.addView(quantityControl)

        val scale = resources.displayMetrics.density
        val deleteSize = (40 * scale + 0.5f).toInt()
        val params = LinearLayout.LayoutParams(deleteSize, deleteSize)
        removeProductButton.layoutParams = params
        removeProductButton.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_700))
        removeProductButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_delete))
        removeProductButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
        removeProductButton.scaleType = ImageView.ScaleType.CENTER_INSIDE

        removeProductButton.setOnClickListener {
            showConfirmationDialog("¿Está seguro de eliminar el producto $productName?") {
                productContainer.removeView(productView)
                section.products = section.products.filterNot { it.productId == recipeProduct.productId }
                updateSectionsUI()
                updateCosts()
            }
        }

        productContainer.addView(productView)
    }

    private fun createQuantityControl(initialValue: Double, onQuantityChanged: (Double) -> Unit): LinearLayout {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(4, 0, 4, 0) }

        val scale = resources.displayMetrics.density
        val buttonSize = (30 * scale + 0.5f).toInt()
        val desiredTextSize = 12f

        val decrementButton = Button(this).apply {
            text = "-"
            textSize = desiredTextSize
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
            setBackgroundColor(ContextCompat.getColor(context, R.color.purple_200))
        }

        val quantityEditText = EditText(this).apply {
            setText(initialValue.toInt().toString())
            textSize = desiredTextSize
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minHeight = (40 * scale + 0.5f).toInt()
        }

        val incrementButton = Button(this).apply {
            text = "+"
            textSize = desiredTextSize
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
            setBackgroundColor(ContextCompat.getColor(context, R.color.purple_200))
        }

        container.addView(decrementButton)
        container.addView(quantityEditText)
        container.addView(incrementButton)

        decrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toIntOrNull() ?: 1
            if (current > 1) {
                val newVal = current - 1
                quantityEditText.setText(newVal.toString())
                onQuantityChanged(newVal.toDouble())
            }
        }

        incrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toIntOrNull() ?: 1
            val newVal = current + 1
            quantityEditText.setText(newVal.toString())
            onQuantityChanged(newVal.toDouble())
        }

        quantityEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString().toIntOrNull() ?: 1
                if (value < 1) {
                    quantityEditText.setText("1")
                    onQuantityChanged(1.0)
                } else {
                    onQuantityChanged(value.toDouble())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return container
    }

    private fun saveRecipe() {
        validateFields { isValid ->
            if (!isValid) return@validateFields

            loader.show()

            val updatedRecipe = Recipe(
                id = recipe?.id ?: "",
                name = binding.recipeNameInput.text.toString(),
                cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
                suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                unit = binding.recipeUnitInput.text.toString().toDoubleOrNull() ?: 1.0,
                onSale = binding.recipeOnSaleCheckbox.isChecked,
                categories = selectedCategories.toList(),
                sections = selectedSections,
                recipes = selectedRecipes
            )

            if (updatedRecipe.id.isEmpty()) {
                recipesHelper.addRecipe(
                    recipe = updatedRecipe,
                    onSuccess = { newRecipe ->
                        loader.hide()
                        sendResultAndFinish(newRecipe)
                    },
                    onError = { e ->
                        loader.hide()
                        Toast.makeText(this, "Error al guardar la receta.", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                )
            } else {
                recipesHelper.updateRecipe(
                    recipeId = updatedRecipe.id,
                    recipe = updatedRecipe,
                    onSuccess = {
                        loader.hide()
                        sendResultAndFinish(updatedRecipe)
                    },
                    onError = { e ->
                        loader.hide()
                        Toast.makeText(this, "Error al actualizar la receta.", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                )
            }
        }
    }

    private fun validateFields(onValidationComplete: (Boolean) -> Unit) {
        val name = binding.recipeNameInput.text.toString().trim()
        val cost = binding.recipeCostInput.text.toString().trim()
        val suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().trim()
        val salePrice = binding.recipeSalePriceInput.text.toString().trim()
        val unitStr = binding.recipeUnitInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "El nombre de la receta es obligatorio.", Toast.LENGTH_SHORT).show()
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
        if (unitStr.isEmpty() || (unitStr.toDoubleOrNull() ?: 0.0) < 1.0) {
            Toast.makeText(this, "Las unidades por receta deben ser al menos 1.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }
        for (section in selectedSections) {
            if (section.products.isEmpty()) {
                Toast.makeText(this, "La sección '${section.name}' debe tener al menos un producto.", Toast.LENGTH_SHORT).show()
                onValidationComplete(false)
                return
            }
        }
        val recipeNewName = binding.recipeNameInput.text.toString().trim()
        recipesHelper.isRecipeNameUnique(recipeNewName, recipe?.id) { isUnique ->
            if (!isUnique) {
                Toast.makeText(this, "Ya existe una receta con este nombre.", Toast.LENGTH_SHORT).show()
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

    // Show a confirmation dialog with a dynamic message.
    private fun showConfirmationDialog(message: String, onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirmación")
            .setMessage(message)
            .setPositiveButton("Sí") { _, _ -> onConfirmed() }
            .setNegativeButton("No", null)
            .show()
    }
}