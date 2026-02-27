package com.estaciondulce.app.activities

import com.estaciondulce.app.models.parcelables.RecipeSection
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
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.parcelables.RecipeNested
import com.estaciondulce.app.models.parcelables.RecipeProduct
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.adapters.RecipeImageAdapter
import com.estaciondulce.app.adapters.ProductSearchAdapter
import com.estaciondulce.app.adapters.RecipeSearchAdapter
import com.estaciondulce.app.utils.ImageUtils
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

    companion object {
        private const val MAX_RECIPE_IMAGES = 10
    }

    private lateinit var binding: ActivityRecipeEditBinding
    private lateinit var loader: CustomLoader
    private var recipe: Recipe? = null
    private var recipeDetail: String = ""
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
            val availableSlots = MAX_RECIPE_IMAGES - totalCurrentImages
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
            binding.recipeCustomizableCheckbox.isChecked = r.customizable
            binding.recipeInStockCheckbox.isChecked = r.inStock
            binding.recipeUnitInput.setText(r.unit.toString())
            binding.recipeDescriptionInput.setText(r.description)
            recipeDetail = r.detail
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
        
        binding.recipeCustomizableCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showCustomizableConfirmationDialog()
            }
        }
        
        binding.addImageButton.setOnClickListener { 
            val totalCurrentImages = existingImageUrls.size + tempImageUrls.size
            if (totalCurrentImages < 5) {
                checkPermissionsAndShowImageDialog()
            } else {
                CustomToast.showInfo(this, "Máximo 5 imágenes permitidas")
            }
        }
        
        binding.detailButton.setOnClickListener {
            showDetailDialog()
        }
        
        loader.hide()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ImageUtils.clearTempFiles(this)
        tempImageFiles.forEach { it.delete() }
    }

    private fun showDetailDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recipe_detail, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        val detailEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.detailEditText)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        
        detailEditText.setText(recipeDetail)
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            recipeDetail = detailEditText.text.toString().trimStart()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showCustomizableConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_customizable_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            binding.recipeCustomizableCheckbox.isChecked = false
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            binding.recipeOnSaleCheckbox.isChecked = true
            binding.recipeOnSaleQueryCheckbox.isChecked = true
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setupCategorySelector(categoriesMap: Map<String, String>) {
        updateCategorySelectorAdapter(categoriesMap)
        binding.categorySelector.setOnItemClickListener { _, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val adapter = binding.categorySelector.adapter as ArrayAdapter<String>
            val selectedName = adapter.getItem(position) ?: return@setOnItemClickListener
            
            if (selectedName == "No se pueden agregar más categorías") {
                binding.categorySelector.setText("")
                return@setOnItemClickListener
            }
            
            val selectedId = categoriesMap.entries.find { it.value == selectedName }?.key ?: return@setOnItemClickListener
            selectedCategories.add(selectedId)
            updateCategoryTags()
            updateCategorySelectorAdapter(categoriesMap) // Update adapter to remove selected category
            binding.categorySelector.setText("")
        }
    }

    private fun updateCategorySelectorAdapter(categoriesMap: Map<String, String>) {
        val availableCategories = categoriesMap.filter { (id, _) -> !selectedCategories.contains(id) }
        val items = if (availableCategories.isEmpty()) {
            listOf("No se pueden agregar más categorías")
        } else {
            availableCategories.values.toList()
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        binding.categorySelector.setAdapter(adapter)
        
        binding.categorySelector.isEnabled = availableCategories.isNotEmpty()
    }

    private fun setupSectionSelector(sectionsMap: Map<String, String>) {
        updateSectionSelectorAdapter(sectionsMap)
        binding.sectionSelector.setOnItemClickListener { _, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val adapter = binding.sectionSelector.adapter as ArrayAdapter<String>
            val selectedName = adapter.getItem(position) ?: return@setOnItemClickListener
            
            if (selectedName == "No se pueden agregar más secciones") {
                binding.sectionSelector.setText("")
                return@setOnItemClickListener
            }
            
            val selectedId = sectionsMap.entries.find { it.value == selectedName }?.key ?: return@setOnItemClickListener
            val newSection = RecipeSection(
                id = selectedId,
                name = selectedName,
                products = mutableListOf()
            )
            selectedSections.add(newSection)
            updateSectionsUI()
            updateSectionSelectorAdapter(sectionsMap) // Update adapter to remove selected section
            binding.sectionSelector.setText("")
        }
    }

    private fun updateSectionSelectorAdapter(sectionsMap: Map<String, String>) {
        val availableSections = sectionsMap.filter { (id, _) -> selectedSections.none { it.id == id } }
        val items = if (availableSections.isEmpty()) {
            listOf("No se pueden agregar más secciones")
        } else {
            availableSections.values.toList()
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        binding.sectionSelector.setAdapter(adapter)
        
        binding.sectionSelector.isEnabled = availableSections.isNotEmpty()
    }

    private fun getProductUnit(productId: String): String {
        val product = repository.productsLiveData.value?.find { it.id == productId }
        val measure = repository.measuresLiveData.value?.find { it.id == product?.measure }
        return measure?.unit ?: "unidad"
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
                    repository.categoriesLiveData.value?.let { categoriesList ->
                        val categoriesMap = categoriesList.associate { it.id to it.name }
                        updateCategorySelectorAdapter(categoriesMap)
                    }
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
            val removeSectionButton = sectionView.findViewById<com.google.android.material.button.MaterialButton>(R.id.removeSectionButton)
            sectionView.tag = section.id
            sectionName.text = section.name
            
            productSearchBar.setOnClickListener {
                showProductSelectionModal(section)
            }
            removeSectionButton.setOnClickListener {
                showConfirmationDialog("¿Está seguro de eliminar la sección ${section.name}?") {
                    selectedSections.remove(section)
                    updateSectionsUI()
                    updateCosts()
                    repository.sectionsLiveData.value?.let { sectionsList ->
                        val sectionsMap = sectionsList.associate { it.id to it.name }
                        updateSectionSelectorAdapter(sectionsMap)
                    }
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

    private fun showProductSelectionModal(section: RecipeSection) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_search, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val productsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.productsRecyclerView)
        val emptyState = dialogView.findViewById<LinearLayout>(R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeButton)

        productsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val allProducts = repository.productsLiveData.value ?: emptyList()
        val availableProducts = allProducts.filter { product ->
            !section.products.any { it.productId == product.id }
        }.sortedBy { it.name }

        val dialogAdapter = ProductSearchAdapter(availableProducts) { selectedProduct ->
            dialog.dismiss()
            showQuantitySelectionDialog(selectedProduct, section)
        }
        
        productsRecyclerView.adapter = dialogAdapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                
                if (query.length < 3) {
                    productsRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.GONE
                    dialogAdapter.updateProducts(emptyList())
                } else {
                    val filtered = availableProducts.filter { 
                        it.name.contains(query, ignoreCase = true) 
                    }.sortedBy { it.name }
                    
                    if (filtered.isEmpty()) {
                        productsRecyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        productsRecyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        dialogAdapter.updateProducts(filtered)
                    }
                }
            }
        })

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showProductSearchPopup(@Suppress("UNUSED_PARAMETER") anchor: View, products: List<Map.Entry<String, Pair<String, Double>>>, section: RecipeSection) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_search, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val productsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.productsRecyclerView)
        val emptyState = dialogView.findViewById<LinearLayout>(R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeButton)

        productsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val productObjects = products.map { entry ->
            com.estaciondulce.app.models.parcelables.Product(
                id = entry.key,
                name = entry.value.first,
                cost = entry.value.second
            )
        }

        val dialogAdapter = ProductSearchAdapter(productObjects) { selectedProduct ->
            if (section.products.any { it.productId == selectedProduct.id }) {
                CustomToast.showError(this, "El producto ya fue añadido en esta sección.")
            } else {
                dialog.dismiss()
                showQuantitySelectionDialog(selectedProduct, section)
            }
        }
        
        productsRecyclerView.adapter = dialogAdapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                val filtered = productObjects.filter { 
                    it.name.contains(query, ignoreCase = true) 
                }.sortedBy { it.name }
                
                if (filtered.isEmpty()) {
                    productsRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    productsRecyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    dialogAdapter.updateProducts(filtered)
                }
            }
        })

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showQuantitySelectionDialog(product: Product, section: RecipeSection) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quantity_selection, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val productNameText = dialogView.findViewById<TextView>(R.id.productNameText)
        val quantityInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.quantityInputLayout)
        val quantityInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val addButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addButton)

        productNameText.text = product.name
        quantityInput.setText("1")
        quantityInput.requestFocus()
        
        val unit = getProductUnit(product.id)
        quantityInputLayout.hint = "Cantidad en $unit"

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        addButton.setOnClickListener {
            val quantity = quantityInput.text.toString().toDoubleOrNull() ?: 1.0
            if (quantity > 0) {
                section.products += RecipeProduct(product.id, quantity)
                updateSectionsUI()
                updateCosts()
                dialog.dismiss()
            } else {
                CustomToast.showError(this, "La cantidad debe ser mayor a 0")
            }
        }

        dialog.show()
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
        val quantityLabel = productView.findViewById<TextView>(R.id.quantityLabel)
        val quantityInput = productView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val removeButton = productView.findViewById<com.google.android.material.button.MaterialButton>(R.id.removeProductButton)
        
        productNameView.text = productName
        productCostView.text = String.format("$%.2f", productCost)
        quantityInput.setText(recipeProduct.quantity.toString())
        
        val unit = getProductUnit(recipeProduct.productId)
        quantityLabel.text = "Cantidad en $unit"
        
        quantityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newQuantity = s.toString().toDoubleOrNull() ?: 0.0
                if (newQuantity > 0) {
                    recipeProduct.quantity = newQuantity
            updateCosts()
        }
            }
        })
        
        removeButton.setOnClickListener {
            showProductDeleteConfirmation(productName) {
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
            customizable = binding.recipeCustomizableCheckbox.isChecked,
            images = (existingImageUrls + tempImageUrls).toList(),
            description = binding.recipeDescriptionInput.text.toString(),
            detail = recipeDetail,
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
            String.format("%.2f", ((salePrice - cost) / cost) * 100).toDouble()
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
        binding.recipeSearchBar.setOnClickListener {
            showRecipeSearchDialog()
        }
    }

    private fun showRecipeSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recipe_search, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val recipesRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recipesRecyclerView)
        val emptyState = dialogView.findViewById<LinearLayout>(R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeButton)

        recipesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val availableRecipes = repository.recipesLiveData.value?.filter { it.id != recipe?.id }?.sortedBy { it.name } ?: emptyList()

        val dialogAdapter = RecipeSearchAdapter(availableRecipes) { selectedRecipe ->
            if (selectedRecipes.any { it.recipeId == selectedRecipe.id }) {
                CustomToast.showError(this, "Esta receta ya fue agregada a las recetas anidadas.")
            } else {
                dialog.dismiss()
                showRecipeQuantitySelectionDialog(selectedRecipe)
            }
        }
        
        recipesRecyclerView.adapter = dialogAdapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                val filtered = availableRecipes.filter { 
                    it.name.contains(query, ignoreCase = true) 
                }.sortedBy { it.name }
                
                if (filtered.isEmpty()) {
                    recipesRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    recipesRecyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    dialogAdapter.updateRecipes(filtered)
                }
            }
        })

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRecipeQuantitySelectionDialog(selectedRecipe: Recipe) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_recipe_quantity_selection, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val recipeNameText = dialogView.findViewById<TextView>(R.id.recipeNameText)
        val quantityInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val addButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addButton)

        recipeNameText.text = selectedRecipe.name
        quantityInput.setText("1")
        quantityInput.requestFocus()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        addButton.setOnClickListener {
            val quantity = quantityInput.text.toString().toIntOrNull() ?: 1
            if (quantity > 0) {
                val newNestedRecipe = RecipeNested(
                    recipeId = selectedRecipe.id,
                    quantity = quantity
                )
                selectedRecipes.add(newNestedRecipe)
                updateNestedRecipesUI()
                updateCosts()
                dialog.dismiss()
            } else {
                CustomToast.showError(this, "La cantidad debe ser mayor a 0")
            }
        }

        dialog.show()
    }

    private fun updateNestedRecipesUI() {
        binding.selectedRecipeContainer.removeAllViews()
        binding.selectedRecipeContainer.visibility = if (selectedRecipes.isNotEmpty()) View.VISIBLE else View.GONE
        repository.recipesLiveData.value?.let { recipesList ->
            for (nested in selectedRecipes) {
                val recipeData = recipesList.find { it.id == nested.recipeId }
                if (recipeData != null) {
                    addRecipeToUI(binding.selectedRecipeContainer, nested, recipeData)
                }
            }
        }
    }

    private fun addRecipeToUI(
        recipeContainer: LinearLayout,
        recipeNested: RecipeNested,
        recipeData: Recipe
    ) {
        val recipeView = LayoutInflater.from(this).inflate(R.layout.item_added_recipe_edit, recipeContainer, false)
        val recipeNameView = recipeView.findViewById<TextView>(R.id.addedRecipeName)
        val recipeCostView = recipeView.findViewById<TextView>(R.id.addedRecipeCost)
        val quantityInput = recipeView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.quantityInput)
        val removeButton = recipeView.findViewById<com.google.android.material.button.MaterialButton>(R.id.removeRecipeButton)
        
        recipeNameView.text = recipeData.name
        recipeCostView.text = String.format("$%.2f", recipeData.cost)
        quantityInput.setText(recipeNested.quantity.toInt().toString())
        
        quantityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newQuantity = s.toString().toIntOrNull() ?: 1
                if (newQuantity > 0) {
                    recipeNested.quantity = newQuantity
                    updateCosts()
                }
            }
        })
        
        removeButton.setOnClickListener {
            showRecipeDeleteConfirmation(recipeData.name) {
                recipeContainer.removeView(recipeView)
                selectedRecipes.removeIf { it.recipeId == recipeNested.recipeId }
                updateNestedRecipesUI()
                updateCosts()
            }
        }
        recipeContainer.addView(recipeView)
    }


    private fun saveRecipe() {
        validateFields { isValid ->
            if (!isValid) return@validateFields
            try {
                loader.show()
                val cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0
                val salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0
                val profitPercentage = if (cost > 0) {
                    String.format("%.2f", ((salePrice - cost) / cost) * 100).toDouble()
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
                customizable = binding.recipeCustomizableCheckbox.isChecked,
                inStock = binding.recipeInStockCheckbox.isChecked,
                images = existingImageUrls.toList(),
                    description = binding.recipeDescriptionInput.text.toString(),
                    detail = recipeDetail,
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
        val isCustomizable = binding.recipeCustomizableCheckbox.isChecked

        if (name.isEmpty()) {
            Toast.makeText(this, "El nombre de la receta es obligatorio.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }
        
        if (!isCustomizable) {
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
        }
        
        if (unitStr.isEmpty() || (unitStr.toDoubleOrNull() ?: 0.0) < 1.0) {
            Toast.makeText(this, "Las unidades por receta deben ser al menos 1.", Toast.LENGTH_SHORT).show()
            onValidationComplete(false)
            return
        }
        
        if (!isCustomizable) {
            for (section in selectedSections) {
                if (section.products.isEmpty()) {
                    Toast.makeText(this, "La sección '${section.name}' debe tener al menos un producto.", Toast.LENGTH_SHORT).show()
                    onValidationComplete(false)
                    return
                }
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

    private fun showProductDeleteConfirmation(productName: String, onConfirmed: () -> Unit) {
        DeleteConfirmationDialog.show(
            context = this,
            itemName = productName,
            itemType = "producto",
            onConfirm = onConfirmed
        )
    }

    private fun showRecipeDeleteConfirmation(recipeName: String, onConfirmed: () -> Unit) {
        DeleteConfirmationDialog.show(
            context = this,
            itemName = recipeName,
            itemType = "receta",
            onConfirm = onConfirmed
        )
    }

    private fun showConfirmationDialog(message: String, onConfirmed: () -> Unit) {
        val itemName: String
        val itemType: String
        
        when {
            message.contains("eliminar la sección ") -> {
                val start = message.indexOf("eliminar la sección ") + 20
                val end = message.indexOf("?")
                itemName = if (start > 19 && end > start) message.substring(start, end).trim() else "sección"
                itemType = "sección"
            }
            message.contains("eliminar esta imagen") -> {
                itemName = "imagen"
                itemType = "imagen"
            }
            else -> {
                itemName = "elemento"
                itemType = "elemento"
            }
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
        binding.addImageButton.isEnabled = totalImages < MAX_RECIPE_IMAGES
        binding.addImageButton.text = if (totalImages < MAX_RECIPE_IMAGES) {
            "Agregar imagen ($totalImages/$MAX_RECIPE_IMAGES)"
        } else {
            "Máximo alcanzado ($MAX_RECIPE_IMAGES/$MAX_RECIPE_IMAGES)"
        }
    }
    
    private fun uploadImagesToTempStorage(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        showImageUploadProgress(true)
        loader.show()
        
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        val validUris = mutableListOf<Uri>()
        val maxFileSize = 4 * 1024 * 1024L // 4MB
        
        uris.forEach { uri ->
            val size = ImageUtils.getImageSize(this, uri)
            if (size > maxFileSize) {
                CustomToast.showError(this, "La imagen es demasiado grande. Máximo 4MB.")
            } else {
                validUris.add(uri)
            }
        }
        
        if (validUris.isEmpty()) {
            showImageUploadProgress(false)
            loader.hide()
            return
        }

        var completedUploads = 0
        val totalUploads = validUris.size
        
        validUris.forEachIndexed { index, originalUri ->
            val fileName = if (validUris.size > 1) {
                "${timestamp}-${index + 1}.jpg"
            } else {
                "${timestamp}.jpg"
            }
            
            val compressedUri = ImageUtils.compressImage(this, originalUri)
            
            storageHelper.uploadTempImage(
                imageUri = compressedUri,
                uid = tempStorageUid,
                fileName = fileName,
                onSuccess = { downloadUrl ->
                    tempImageUrls.add(downloadUrl)
                    tempUrlToOriginalUriMap[downloadUrl] = compressedUri // Store compressed URI for final upload
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
        binding.addImageButton.isEnabled = !show && totalImages < MAX_RECIPE_IMAGES
    }
}
