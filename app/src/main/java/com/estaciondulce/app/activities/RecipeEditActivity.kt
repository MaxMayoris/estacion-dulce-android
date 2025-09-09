package com.estaciondulce.app.activities

import RecipeSection
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ActivityRecipeEditBinding
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.helpers.StorageHelper
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.models.RecipeNested
import com.estaciondulce.app.models.RecipeProduct
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.adapters.RecipeImageAdapter
import android.util.TypedValue
import java.io.File
import java.io.IOException

val Int.dp: Int
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        android.content.res.Resources.getSystem().displayMetrics
    ).toInt()

class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private lateinit var loader: CustomLoader
    private var recipe: Recipe? = null
    private val recipesHelper = RecipesHelper()
    private val storageHelper = StorageHelper()
    private val selectedSections = mutableListOf<RecipeSection>()
    private val selectedCategories = mutableSetOf<String>()
    private val selectedRecipes = mutableListOf<RecipeNested>()
    private val repository = FirestoreRepository
    
    private var currentImageUris: List<Uri> = emptyList()
    private var existingImageUrls: MutableList<String> = mutableListOf()
    private var tempImageUrls: MutableList<String> = mutableListOf()
    private var tempImageFiles: MutableList<File> = mutableListOf()
    private val tempUrlToOriginalUriMap: MutableMap<String, Uri> = mutableMapOf()
    private val imagesToDeleteFromStorage: MutableList<String> = mutableListOf()
    private lateinit var imageAdapter: RecipeImageAdapter
    private val sessionTempId = "temp_${System.currentTimeMillis()}"
    private val tempStorageUid: String
        get() = if (recipe?.id?.isNotEmpty() == true) recipe!!.id else sessionTempId
    private val currentUserId: String
        get() = recipe?.id?.takeIf { it.isNotEmpty() } ?: sessionTempId
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val totalCurrentImages = existingImageUrls.size + tempImageUrls.size
            val availableSlots = 5 - totalCurrentImages
            val urisToProcess = uris.take(availableSlots)
            
            if (uris.size > availableSlots) {
                CustomToast.showInfo(this, "Solo puedes agregar $availableSlots imágenes más")
            }
            
            if (urisToProcess.isNotEmpty()) {
                currentImageUris = urisToProcess
                uploadImagesToTempStorage(urisToProcess)
            }
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageFiles.isNotEmpty()) {
            val tempFile = tempImageFiles.last()
            val uri = Uri.fromFile(tempFile)
            uploadImagesToTempStorage(listOf(uri))
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val storageGranted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val mediaGranted = permissions[android.Manifest.permission.READ_MEDIA_IMAGES] ?: false
        
        if (cameraGranted) {
            val hasStorageAccess = storageGranted || mediaGranted
            if (hasStorageAccess) {
                showImageSelectionDialog()
            } else {
                showImageSelectionDialogCameraOnly()
            }
        } else {
            handleCameraPermissionDenied()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (intent.hasExtra("recipe")) "Editar Receta" else "Agregar Receta"
        @Suppress("DEPRECATION")
        recipe = intent.getParcelableExtra<Recipe>("recipe")

        repository.categoriesLiveData.observe(this, Observer { categoriesList ->
            val categoriesMap = categoriesList.associate { it.id to it.name }
            setupCategorySelector(categoriesMap)
        })
        repository.sectionsLiveData.observe(this, Observer { sectionsList ->
            val sectionsMap = sectionsList.associate { it.id to it.name }
            setupSectionSelector(sectionsMap)
        })
        repository.recipesLiveData.observe(this, Observer { setupRecipeSearchBar() })

        imageAdapter = RecipeImageAdapter { imageUrl ->
            deleteImage(imageUrl)
        }
        binding.imagesRecyclerView.adapter = imageAdapter

        recipe?.let { r ->
            binding.recipeNameInput.setText(r.name)
            binding.recipeCostInput.setText(String.format("%.2f", r.cost))
            binding.recipeSuggestedPriceInput.setText(String.format("%.2f", r.suggestedPrice))
            binding.recipeSalePriceInput.setText(String.format("%.2f", r.salePrice))
            binding.recipeOnSaleCheckbox.isChecked = r.onSale
            binding.recipeOnSaleQueryCheckbox.isChecked = r.onSaleQuery
            binding.recipeUnitInput.setText(r.unit.toString())
            binding.recipeDescriptionInput.setText(r.description)
            existingImageUrls.clear()
            existingImageUrls.addAll(r.images)
            tempImageUrls.clear()
            selectedSections.clear()
            selectedCategories.clear()
            selectedSections.addAll(r.sections.filter { it.products.isNotEmpty() })
            selectedCategories.addAll(r.categories)
            selectedRecipes.clear()
            selectedRecipes.addAll(r.recipes)
            updateSectionsUI()
            updateCategoryTags()
            updateNestedRecipesUI()
            updateCosts()
            updateProfitPercentage()
            updateImageGallery()
        }

        binding.recipeUnitInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val unitValue = s.toString().toDoubleOrNull() ?: 1.0
                if (unitValue < 1.0) binding.recipeUnitInput.setText("1")
                updateCosts()
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        binding.recipeCostInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateProfitPercentage()
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        binding.recipeSalePriceInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateProfitPercentage()
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        loader.show()
        binding.saveRecipeButton.setOnClickListener { saveRecipe() }
        
        binding.addImageButton.setOnClickListener { 
            val totalCurrentImages = existingImageUrls.size + tempImageUrls.size
            if (totalCurrentImages < 5) {
                checkPermissionsAndShowImageDialog()
            } else {
                CustomToast.showInfo(this, "Máximo 5 imágenes permitidas")
            }
        }
        
        loader.hide()
    }

    private fun setupCategorySelector(categoriesMap: Map<String, String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoriesMap.values.toList())
        binding.categorySelector.setAdapter(adapter)
        binding.categorySelector.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position) ?: return@setOnItemClickListener
            val selectedId = categoriesMap.entries.find { it.value == selectedName }?.key ?: return@setOnItemClickListener
            if (!selectedCategories.contains(selectedId)) {
                selectedCategories.add(selectedId)
                updateCategoryTags()
                binding.categorySelector.setText("")
            } else {
                Toast.makeText(this@RecipeEditActivity, "La categoría ya fue añadida", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSectionSelector(sectionsMap: Map<String, String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sectionsMap.values.toList())
        binding.sectionSelector.setAdapter(adapter)
        binding.sectionSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position) ?: return@setOnItemClickListener
            val selectedId = sectionsMap.entries.find { it.value == selectedName }?.key ?: return@setOnItemClickListener
            if (selectedSections.none { it.id == selectedId }) {
                val newSection = RecipeSection(
                    id = selectedId,
                    name = selectedName,
                    products = mutableListOf()
                )
                selectedSections.add(newSection)
                updateSectionsUI()
                binding.sectionSelector.setText("")
            } else {
                Toast.makeText(this@RecipeEditActivity, "La sección ya fue añadida", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCategoryTags() {
        binding.categoryTagsContainer.removeAllViews()
        if (selectedCategories.isEmpty()) {
            binding.categoryTagsContainer.visibility = View.GONE
            return
        }
        repository.categoriesLiveData.value?.let { catList ->
            val catMap = catList.associate { it.id to it.name }
            for (catId in selectedCategories) {
                val tagView = LayoutInflater.from(this).inflate(R.layout.item_category_tag, binding.categoryTagsContainer, false)
                val textView = tagView.findViewById<TextView>(R.id.categoryTagName)
                val deleteButton = tagView.findViewById<ImageView>(R.id.categoryTagDelete)
                textView.text = catMap[catId] ?: ""
                deleteButton.setOnClickListener {
                    selectedCategories.remove(catId)
                    updateCategoryTags()
                }
                binding.categoryTagsContainer.addView(tagView)
            }
            binding.categoryTagsContainer.visibility = View.VISIBLE
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
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    if (query.length >= 3) {
                        repository.productsLiveData.value?.let { productsList ->
                            val filtered = productsList.filter { it.name.contains(query, ignoreCase = true) }
                                .associate { it.id to Pair(it.name, it.cost) }
                            showProductSearchPopup(productSearchBar, filtered.entries.toList(), section)
                        }
                    }
                }
                override fun afterTextChanged(s: Editable?) { }
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
                val productEntry = repository.productsLiveData.value?.find { it.id == product.productId }
                    ?.let { Pair(it.name, it.cost) }
                if (productEntry != null)
                    addProductToUI(productContainer, section, product, productEntry.first, productEntry.second)
                else
                    addProductToUI(productContainer, section, product, "Producto desconocido", 0.0)
            }
            binding.sectionsContainer.addView(sectionView)
        }
    }

    private fun showProductSearchPopup(anchor: View, products: List<Map.Entry<String, Pair<String, Double>>>, section: RecipeSection) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, products.map { it.value.first })
        val popup = ListPopupWindow(this).apply {
            anchorView = anchor
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                val selected = products[position]
                if (section.products.any { it.productId == selected.key }) {
                    Toast.makeText(this@RecipeEditActivity, "El producto ya fue añadido en esta sección.", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@setOnItemClickListener
                }
                section.products += RecipeProduct(selected.key, 1.0)
                if (anchor is EditText) anchor.text.clear()
                updateSectionsUI()
                updateCosts()
                dismiss()
            }
        }
        popup.show()
    }

    private fun addProductToUI(
        productContainer: LinearLayout,
        section: RecipeSection,
        recipeProduct: RecipeProduct,
        productName: String,
        productCost: Double
    ) {
        val productView = LayoutInflater.from(this).inflate(R.layout.item_added_product_recipe_edit, productContainer, false)
        val productNameView = productView.findViewById<TextView>(R.id.addedProductName)
        val productCostView = productView.findViewById<TextView>(R.id.addedProductCost)
        val quantityContainer = productView.findViewById<LinearLayout>(R.id.productQuantity)
        val removeButton = productView.findViewById<com.google.android.material.button.MaterialButton>(R.id.removeProductButton)
        productNameView.text = productName
        productCostView.text = String.format("%.2f", productCost)
        val quantityControl = createQuantityControl(recipeProduct.quantity) { newQty ->
            recipeProduct.quantity = newQty
            updateCosts()
        }
        quantityContainer.removeAllViews()
        quantityContainer.addView(quantityControl)
        removeButton.setOnClickListener {
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
        container.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(8, 0, 8, 0)
        }
        container.gravity = android.view.Gravity.CENTER_VERTICAL
        
        val decrementButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "−"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            cornerRadius = 20.dp
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
            strokeColor = ContextCompat.getColorStateList(context, R.color.purple_700)
            strokeWidth = 2.dp
            contentDescription = "Decrementar cantidad"
        }
        
        val quantityEditText = EditText(this).apply {
            setText(String.format("%.2f", initialValue))
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(8.dp, 0, 8.dp, 0)
            }
            minWidth = 60.dp
            background = ContextCompat.getDrawable(context, R.drawable.input_background)
        }
        
        val incrementButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "+"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            cornerRadius = 20.dp
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
            strokeColor = ContextCompat.getColorStateList(context, R.color.purple_700)
            strokeWidth = 2.dp
            contentDescription = "Incrementar cantidad"
        }
        
        container.addView(decrementButton)
        container.addView(quantityEditText)
        container.addView(incrementButton)
        
        decrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
            val newVal = (current - 1.0).coerceAtLeast(0.0)
            quantityEditText.setText(String.format("%.2f", newVal))
            onQuantityChanged(newVal)
        }
        incrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toDoubleOrNull() ?: 0.0
            val newVal = current + 1.0
            quantityEditText.setText(String.format("%.2f", newVal))
            onQuantityChanged(newVal)
        }
        quantityEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString().toDoubleOrNull() ?: 0.0
                if (value < 0.0) {
                    quantityEditText.setText("0.00")
                    onQuantityChanged(0.0)
                } else {
                    onQuantityChanged(value)
                }
            }
            override fun afterTextChanged(s: Editable?) { }
        })
        return container
    }

    private fun createIntegerQuantityControl(initialValue: Int, onQuantityChanged: (Int) -> Unit): LinearLayout {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) }
        container.gravity = android.view.Gravity.CENTER_VERTICAL

        val decrementButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "−"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            cornerRadius = 20.dp
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
            strokeColor = ContextCompat.getColorStateList(context, R.color.purple_700)
            strokeWidth = 2.dp
            contentDescription = "Decrementar cantidad"
        }

        val quantityEditText = EditText(this).apply {
            setText(initialValue.toString())
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8.dp, 0, 8.dp, 0)
            }
            minWidth = 60.dp
            background = ContextCompat.getDrawable(context, R.drawable.input_background)
        }

        val incrementButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "+"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            cornerRadius = 20.dp
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
            strokeColor = ContextCompat.getColorStateList(context, R.color.purple_700)
            strokeWidth = 2.dp
            contentDescription = "Incrementar cantidad"
        }

        container.addView(decrementButton)
        container.addView(quantityEditText)
        container.addView(incrementButton)

        decrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toIntOrNull() ?: 0
            if (current > 1) {
                val newVal = current - 1
                quantityEditText.setText(newVal.toString())
                onQuantityChanged(newVal)
            }
        }

        incrementButton.setOnClickListener {
            val current = quantityEditText.text.toString().toIntOrNull() ?: 0
            val newVal = current + 1
            quantityEditText.setText(newVal.toString())
            onQuantityChanged(newVal)
        }

        quantityEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString().toIntOrNull() ?: 1
                if (value < 1) {
                    quantityEditText.setText("1")
                    onQuantityChanged(1)
                } else {
                    onQuantityChanged(value)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return container
    }

    private fun updateCosts() {
        val productsMap = repository.productsLiveData.value?.associate { it.id to Pair(it.name, it.cost) } ?: emptyMap()
        val recipesMap = repository.recipesLiveData.value?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        val updatedRecipe = Recipe(
            id = recipe?.id ?: "",
            name = binding.recipeNameInput.text.toString(),
            cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
            suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            profitPercentage = 0.0, 
            unit = binding.recipeUnitInput.text.toString().toIntOrNull() ?: 1,
            onSale = binding.recipeOnSaleCheckbox.isChecked,
            onSaleQuery = binding.recipeOnSaleQueryCheckbox.isChecked,
            images = (existingImageUrls + tempImageUrls).toList(),
            description = binding.recipeDescriptionInput.text.toString(),
            categories = selectedCategories.toList(),
            sections = selectedSections,
            recipes = selectedRecipes
        )
        val (costPerUnit, suggestedPrice) = recipesHelper.calculateCostAndSuggestedPrice(updatedRecipe, productsMap, recipesMap)
        binding.recipeCostInput.setText(String.format("%.2f", costPerUnit))
        binding.recipeSuggestedPriceInput.setText(String.format("%.2f", suggestedPrice))
        updateProfitPercentage()
    }

    private fun updateProfitPercentage() {
        val cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0
        val salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0
        
        val profitPercentage = if (cost > 0) {
            ((salePrice - cost) / cost) * 100
        } else {
            0.0
        }
        
        val formattedPercentage = String.format("%.1f%%", profitPercentage)
        binding.profitPercentageValue.text = formattedPercentage
        
        val color = if (profitPercentage >= 0) {
            ContextCompat.getColor(this, R.color.success_green)
        } else {
            ContextCompat.getColor(this, R.color.error_red)
        }
        binding.profitPercentageValue.setTextColor(color)
    }

    private fun setupRecipeSearchBar() {
        val recipeSearchAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val popupWindow = ListPopupWindow(this).apply {
            anchorView = binding.recipeSearchBar
            setAdapter(recipeSearchAdapter)
            setOnItemClickListener { _, _, position, _ ->
                repository.recipesLiveData.value?.let { recipesList ->
                    val selectedRecipe = recipesList.find { it.name == recipeSearchAdapter.getItem(position) }
                    selectedRecipe?.let { displaySelectedRecipe(it) }
                }
                dismiss()
            }
        }
        binding.recipeSearchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 3) {
                    repository.recipesLiveData.value?.let { recipesList ->
                        val filtered = recipesList.filter { it.name.contains(query, ignoreCase = true) && it.id != recipe?.id }
                        recipeSearchAdapter.clear()
                        recipeSearchAdapter.addAll(filtered.map { it.name })
                        recipeSearchAdapter.notifyDataSetChanged()
                        if (filtered.isNotEmpty()) popupWindow.show() else popupWindow.dismiss()
                    }
                } else {
                    popupWindow.dismiss()
                }
            }
            override fun afterTextChanged(s: Editable?) { }
        })
    }

    private fun updateNestedRecipesUI() {
        binding.selectedRecipeContainer.removeAllViews()
        binding.selectedRecipeContainer.visibility = if (selectedRecipes.isNotEmpty()) View.VISIBLE else View.GONE
        repository.recipesLiveData.value?.let { recipesList ->
            for (nested in selectedRecipes) {
                val recipeData = recipesList.find { it.id == nested.recipeId }
                if (recipeData != null) {
                    val recipeRow = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
                    val quantityControl = createIntegerQuantityControl(nested.quantity) { newQuantity ->
                        nested.quantity = newQuantity
                        updateCosts()
                    }

                    val deleteButton = com.google.android.material.button.MaterialButton(this).apply {
                        layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).apply { setMargins(8, 0, 0, 0) }
                        cornerRadius = 20.dp
                        backgroundTintList = ContextCompat.getColorStateList(this@RecipeEditActivity, R.color.purple_700)
                        setIcon(ContextCompat.getDrawable(this@RecipeEditActivity, R.drawable.ic_delete))
                        iconTint = ContextCompat.getColorStateList(this@RecipeEditActivity, R.color.white)
                        iconSize = 20.dp
                        iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                        iconPadding = 0
                        contentDescription = "Eliminar receta"
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
    }

    private fun displaySelectedRecipe(recipe: Recipe) {
        binding.selectedRecipeContainer.visibility = View.VISIBLE
        if (binding.selectedRecipeContainer.findViewWithTag<LinearLayout>(recipe.id) != null) {
            Toast.makeText(this, "Esta receta ya fue añadida.", Toast.LENGTH_SHORT).show()
            return
        }
        val recipeRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            text = String.format("%.2f", recipe.cost)
            setPadding(8, 8, 8, 8)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        val quantityControl = createIntegerQuantityControl(1) { newQuantity ->
            val nested = selectedRecipes.find { it.recipeId == recipe.id } ?: RecipeNested(recipeId = recipe.id, quantity = 1)
            nested.quantity = newQuantity
            if (!selectedRecipes.contains(nested)) {
                selectedRecipes.add(nested)
            }
            updateCosts()
        }
        val deleteButton = com.google.android.material.button.MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp).apply { setMargins(8, 0, 0, 0) }
            cornerRadius = 20.dp
            backgroundTintList = ContextCompat.getColorStateList(this@RecipeEditActivity, R.color.purple_700)
            setIcon(ContextCompat.getDrawable(this@RecipeEditActivity, R.drawable.ic_delete))
            iconTint = ContextCompat.getColorStateList(this@RecipeEditActivity, R.color.white)
            iconSize = 20.dp
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            contentDescription = "Eliminar receta"
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
            selectedRecipes.add(RecipeNested(recipeId = recipe.id, quantity = 1))
        }
        updateCosts()
        binding.recipeSearchBar.text?.clear()
    }

    private fun saveRecipe() {
        validateFields { isValid ->
            if (!isValid) return@validateFields
            try {
                loader.show()
                val cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0
                val salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0
                val profitPercentage = if (cost > 0) {
                    ((salePrice - cost) / cost) * 100
                } else {
                    0.0
                }

                val updatedRecipe = Recipe(
                    id = recipe?.id ?: "",
                    name = binding.recipeNameInput.text.toString(),
                    cost = cost,
                    suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull()
                        ?: 0.0,
                    salePrice = salePrice,
                    profitPercentage = profitPercentage,
                    unit = binding.recipeUnitInput.text.toString().toIntOrNull() ?: 1,
                    onSale = binding.recipeOnSaleCheckbox.isChecked,
                    onSaleQuery = binding.recipeOnSaleQueryCheckbox.isChecked,
                    images = existingImageUrls.toList(),
                    description = binding.recipeDescriptionInput.text.toString(),
                    categories = selectedCategories.toList(),
                    sections = selectedSections,
                    recipes = selectedRecipes
                )
                if (updatedRecipe.id.isEmpty()) {
                recipesHelper.addRecipe(
                    recipe = updatedRecipe,
                    onSuccess = { newRecipe ->
                        if (tempImageUrls.isNotEmpty()) {
                            uploadTempImagesToFinalLocation(
                                tempImageUrls = tempImageUrls,
                                recipeId = newRecipe.id,
                                onSuccess = { finalImageUrls ->
                                    if (imagesToDeleteFromStorage.isNotEmpty()) {
                                        storageHelper.deleteImagesFromStorage(
                                            imageUrls = imagesToDeleteFromStorage,
                                            onSuccess = {
                                                val finalRecipe = newRecipe.copy(
                                                    images = existingImageUrls + finalImageUrls
                                                )
                                                updateRecipeAndFinish(finalRecipe)
                                            },
                                            onError = { error ->
                                                loader.hide()
                                                CustomToast.showError(
                                                    this,
                                                    "Error al eliminar imágenes: ${error.message}"
                                                )
                                                error.printStackTrace()
                                            }
                                        )
                                    } else {
                                        val finalRecipe = newRecipe.copy(
                                            images = existingImageUrls + finalImageUrls
                                        )
                                        updateRecipeAndFinish(finalRecipe)
                                    }
                                },
                                onError = { error ->
                                    loader.hide()
                                    CustomToast.showError(
                                        this,
                                        "Error al subir imágenes: ${error.message}"
                                    )
                                    error.printStackTrace()
                                }
                            )
                        } else {
                            val finalRecipe = newRecipe.copy(
                                images = existingImageUrls
                            )
                            updateRecipeAndFinish(finalRecipe)
                        }
                    },
                    onError = { error ->
                        loader.hide()
                        CustomToast.showError(this, "Error al guardar la receta: ${error.message}")
                        error.printStackTrace()
                    }
                )
            } else {
                if (tempImageUrls.isNotEmpty()) {
                    uploadTempImagesToFinalLocation(
                        tempImageUrls = tempImageUrls,
                        recipeId = updatedRecipe.id,
                        onSuccess = { finalImageUrls ->
                            if (imagesToDeleteFromStorage.isNotEmpty()) {
                                storageHelper.deleteImagesFromStorage(
                                    imageUrls = imagesToDeleteFromStorage,
                                    onSuccess = {
                                        val finalRecipe = updatedRecipe.copy(
                                            images = existingImageUrls + finalImageUrls
                                        )
                                        updateRecipeAndFinish(finalRecipe)
                                    },
                                    onError = { error ->
                                        loader.hide()
                                        CustomToast.showError(
                                            this,
                                            "Error al eliminar imágenes: ${error.message}"
                                        )
                                        error.printStackTrace()
                                    }
                                )
                            } else {
                                val finalRecipe = updatedRecipe.copy(
                                    images = existingImageUrls + finalImageUrls
                                )
                                updateRecipeAndFinish(finalRecipe)
                            }
                        },
                        onError = { error ->
                            loader.hide()
                            CustomToast.showError(this, "Error al subir imágenes: ${error.message}")
                            error.printStackTrace()
                        }
                    )
                } else {
                    if (imagesToDeleteFromStorage.isNotEmpty()) {
                        storageHelper.deleteImagesFromStorage(
                            imageUrls = imagesToDeleteFromStorage,
                            onSuccess = {
                                val finalRecipe = updatedRecipe.copy(
                                    images = existingImageUrls
                                )
                                updateRecipeAndFinish(finalRecipe)
                            },
                            onError = { error ->
                                loader.hide()
                                CustomToast.showError(
                                    this,
                                    "Error al eliminar imágenes: ${error.message}"
                                )
                                error.printStackTrace()
                            }
                        )
                    } else {
                        val finalRecipe = updatedRecipe.copy(
                            images = existingImageUrls
                        )
                        updateRecipeAndFinish(finalRecipe)
                    }
                }
            }
            } catch (e: Exception) {
                loader.hide()
                CustomToast.showError(this, "Error al guardar la receta: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun updateRecipeAndFinish(finalRecipe: Recipe) {
        recipesHelper.updateRecipe(
            recipeId = finalRecipe.id,
            recipe = finalRecipe,
            onSuccess = {
                loader.hide()
                sendResultAndFinish(finalRecipe)
            },
            onError = { e ->
                loader.hide()
                CustomToast.showError(this, "Error al actualizar receta: ${e.message}")
                e.printStackTrace()
            }
        )
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
        val resultIntent = Intent().apply { putExtra("updatedRecipe", updatedRecipe) }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun showConfirmationDialog(message: String, onConfirmed: () -> Unit) {
        val itemName = when {
            message.contains("producto") -> {
                val start = message.indexOf("'") + 1
                val end = message.lastIndexOf("'")
                if (start > 0 && end > start) message.substring(start, end) else "producto"
            }
            message.contains("sección") -> {
                val start = message.indexOf("sección ") + 8
                val end = message.indexOf("?")
                if (start > 7 && end > start) message.substring(start, end).trim() else "sección"
            }
            message.contains("receta") -> {
                val start = message.indexOf("receta ") + 7
                val end = message.indexOf("?")
                if (start > 6 && end > start) message.substring(start, end).trim() else "receta"
            }
            message.contains("imagen") -> "imagen"
            else -> "elemento"
        }
        
        val itemType = when {
            message.contains("producto") -> "producto"
            message.contains("sección") -> "sección"
            message.contains("receta") -> "receta"
            message.contains("imagen") -> "imagen"
            else -> "elemento"
        }
        
        DeleteConfirmationDialog.show(
            context = this,
            itemName = itemName,
            itemType = itemType,
            onConfirm = onConfirmed
        )
    }
    
    private fun checkPermissionsAndShowImageDialog() {
        val cameraPermission = android.Manifest.permission.CAMERA
        val storagePermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
        val mediaPermission = android.Manifest.permission.READ_MEDIA_IMAGES
        
        val cameraGranted = ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED
        val storageGranted = ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED
        val mediaGranted = ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED
        
        if (cameraGranted && (storageGranted || mediaGranted)) {
            showImageSelectionDialog()
        } else if (cameraGranted) {
            showImageSelectionDialogCameraOnly()
        } else {
            requestImagePermissions()
        }
    }
    
    private fun requestImagePermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.CAMERA)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            showImageSelectionDialog()
        }
    }
    
    private fun showImageSelectionDialog() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        if (isCameraAvailable()) {
            options.add("Cámara")
            actions.add { openCamera() }
        }
        
        options.add("Galería")
        actions.add { openGallery() }
        
        if (options.isEmpty()) {
            Toast.makeText(this, "No hay opciones de imagen disponibles", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showImageSelectionDialogCameraOnly() {
        AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen")
            .setMessage("Solo tienes acceso a la cámara. ¿Deseas tomar una foto?")
            .setPositiveButton("Tomar foto") { _, _ ->
                openCamera()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun handleCameraPermissionDenied() {
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this, android.Manifest.permission.CAMERA
        )
        
        if (shouldShowRationale) {
            AlertDialog.Builder(this)
                .setTitle("Permiso de cámara requerido")
                .setMessage("Para tomar fotos de recetas, necesitas otorgar permiso de cámara.")
                .setPositiveButton("Intentar de nuevo") { _, _ ->
                    requestImagePermissions()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permiso de cámara requerido")
                .setMessage("Para tomar fotos, necesitas habilitar el permiso de cámara en la configuración de la aplicación.")
                .setPositiveButton("Ir a configuración") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
    
    private fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de cámara no otorgado", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val tempFile = createImageFile()
            tempImageFiles.add(tempFile)
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            cameraLauncher.launch(photoURI)
        } catch (e: IOException) {
            Toast.makeText(this, "Error al crear archivo de imagen: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir la cámara: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    private fun openGallery() {
        val hasStoragePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        if (hasStoragePermission) {
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permiso de galería no otorgado", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createImageFile(): File {
        val imageFileName = "JPEG_${System.currentTimeMillis()}_"
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    private fun isCameraAvailable(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    
    private fun updateImageGallery() {
        val allImages = existingImageUrls + tempImageUrls
        imageAdapter.updateImages(allImages)
        val totalImages = allImages.size
        binding.addImageButton.isEnabled = totalImages < 5
        binding.addImageButton.text = if (totalImages < 5) {
            "Agregar imagen ($totalImages/5)"
        } else {
            "Máximo alcanzado (5/5)"
        }
    }
    
    private fun uploadImagesToTempStorage(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        showImageUploadProgress(true)
        loader.show("Subiendo imágenes...")
        
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        var completedUploads = 0
        val totalUploads = uris.size
        
        uris.forEachIndexed { index, uri ->
            val fileName = if (uris.size > 1) {
                "${timestamp}-${index + 1}.jpg"
            } else {
                "${timestamp}.jpg"
            }
            
            storageHelper.uploadTempImage(
                imageUri = uri,
                uid = tempStorageUid,
                fileName = fileName,
                onSuccess = { downloadUrl ->
                    tempImageUrls.add(downloadUrl)
                    tempUrlToOriginalUriMap[downloadUrl] = uri
                    completedUploads++
                    
                    if (completedUploads == totalUploads) {
                        showImageUploadProgress(false)
                        loader.hide()
                        updateImageGallery()
                        CustomToast.showSuccess(this, "Imágenes subidas exitosamente")
                    }
                },
                onError = { error ->
                    completedUploads++
                    if (completedUploads == totalUploads) {
                        showImageUploadProgress(false)
                        loader.hide()
                        updateImageGallery()
                        CustomToast.showError(this, "Error al subir algunas imágenes: ${error.message}")
                    }
                    error.printStackTrace()
                }
            )
        }
    }
    
    private fun deleteImage(imageUrl: String) {
        showConfirmationDialog("¿Está seguro de eliminar esta imagen?") {
            if (tempImageUrls.contains(imageUrl)) {
                tempImageUrls.remove(imageUrl)
                tempUrlToOriginalUriMap.remove(imageUrl)
                updateImageGallery()
                CustomToast.showSuccess(this, "Imagen eliminada")
            } else if (existingImageUrls.contains(imageUrl)) {
                existingImageUrls.remove(imageUrl)
                imagesToDeleteFromStorage.add(imageUrl)
                updateImageGallery()
                CustomToast.showSuccess(this, "Imagen eliminada")
            }
        }
    }
    
    private fun clearAllImages() {
        existingImageUrls.clear()
        tempImageUrls.clear()
        tempUrlToOriginalUriMap.clear()
        imagesToDeleteFromStorage.clear()
        currentImageUris = emptyList()
        tempImageFiles.forEach { it.delete() }
        tempImageFiles.clear()
        updateImageGallery()
    }
    
    private fun uploadTempImagesToFinalLocation(
        tempImageUrls: List<String>,
        recipeId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (tempImageUrls.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        
        val urisToUpload = mutableListOf<Uri>()
        val fileNames = mutableListOf<String>()
        
        tempImageUrls.forEach { tempUrl ->
            val originalUri = findOriginalUriForTempUrl(tempUrl)
            if (originalUri != null) {
                urisToUpload.add(originalUri)
                val fileName = tempUrl.substringAfterLast("/").substringBefore("?")
                    .replace("%2F", "/")
                    .substringAfterLast("/")
                fileNames.add(fileName)
            }
        }
        
        if (urisToUpload.isEmpty()) {
            onError(Exception("No se encontraron las URIs originales para las imágenes temp"))
            return
        }
        
        var completedUploads = 0
        val totalUploads = urisToUpload.size
        val finalImageUrls = mutableListOf<String>()
        
        urisToUpload.forEachIndexed { index, uri ->
            val fileName = fileNames[index]
            
            storageHelper.uploadRecipeImageWithName(
                imageUri = uri,
                recipeId = recipeId,
                fileName = fileName,
                onSuccess = { downloadUrl ->
                    finalImageUrls.add(downloadUrl)
                    completedUploads++
                    
                    if (completedUploads == totalUploads) {
                        onSuccess(finalImageUrls)
                    }
                },
                onError = { error ->
                    completedUploads++
                    if (completedUploads == totalUploads) {
                        if (finalImageUrls.isNotEmpty()) {
                            onSuccess(finalImageUrls)
                        } else {
                            onError(error)
                        }
                    }
                }
            )
        }
    }
    
    private fun findOriginalUriForTempUrl(tempUrl: String): Uri? {
        return tempUrlToOriginalUriMap[tempUrl]
    }

    private fun showImageUploadProgress(show: Boolean) {
        binding.imageUploadProgress.visibility = if (show) View.VISIBLE else View.GONE
        val totalImages = existingImageUrls.size + tempImageUrls.size
        binding.addImageButton.isEnabled = !show && totalImages < 5
    }
}
