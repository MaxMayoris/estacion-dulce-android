package com.estaciondulce.app.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.estaciondulce.app.databinding.ActivityProductEditBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.adapters.TableAdapter
import com.estaciondulce.app.adapters.RecipeTableAdapter
import com.estaciondulce.app.models.TableColumnConfig
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.databinding.TableRowDynamicBinding

/**
 * Activity to add or update a product.
 */
class ProductEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductEditBinding
    private val productsHelper = ProductsHelper()
    private val repository = FirestoreRepository
    private var currentProduct: Product? = null
    private lateinit var customLoader: CustomLoader
    private var selectedTab = "info"

    /**
     * Initializes the activity, sets up LiveData observers, and pre-populates fields if editing.
     * Supports deep linking from FCM notifications using PRODUCT_ID intent extra.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customLoader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        @Suppress("DEPRECATION")
        currentProduct = intent.getParcelableExtra<Product>("PRODUCT")
        
        val productIdFromNotification = intent.getStringExtra("PRODUCT_ID")
        if (currentProduct == null && !productIdFromNotification.isNullOrEmpty()) {
            customLoader.show()
            repository.productsLiveData.observe(this) { products ->
                if (currentProduct == null) {
                    currentProduct = products.find { it.id == productIdFromNotification }
                    customLoader.hide()
                    populateFields()
                    setupTabs()
                }
            }
        }
        
        supportActionBar?.title =
            if (currentProduct != null) "Editar Producto" else "Agregar Producto"

        repository.measuresLiveData.observe(this) { measures ->
            setupMeasureSpinner(measures)
        }

        repository.recipesLiveData.observe(this) { recipes ->
            if (selectedTab == "recipes") {
                setupRecipesTable(recipes)
            }
        }

        populateFields()
        setupTabs()
        binding.saveProductButton.setOnClickListener { saveProduct() }
    }
    
    /**
     * Populates input fields with current product data if available.
     */
    private fun populateFields() {
        currentProduct?.let { product ->
            binding.productNameInput.setText(product.name)
            binding.productStockInput.setText(product.quantity.toString())
            binding.productCostInput.setText(product.cost.toString())
            binding.productSalePriceInput.setText(product.salePrice.toString())
            binding.productMinimumQuantityInput.setText(product.minimumQuantity.toString())
        }
    }

    /**
     * Launcher for RecipeEditActivity.
     */
    private val recipeEditActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            repository.recipesLiveData.value?.let { recipes ->
                setupRecipesTable(recipes)
            }
        }
    }

    /**
     * Handles action bar item selection.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Sets up tab functionality and click listeners.
     */
    private fun setupTabs() {
        if (currentProduct == null) {
            binding.tabsContainer.visibility = android.view.View.GONE
            binding.infoTabContent.visibility = android.view.View.VISIBLE
            binding.recipesTabContent.visibility = android.view.View.GONE
            return
        }
        
        binding.infoTab.setOnClickListener {
            selectTab("info")
        }
        
        binding.recipesTab.setOnClickListener {
            selectTab("recipes")
        }
        
        selectTab("info")
    }

    /**
     * Handles tab selection and updates UI accordingly.
     */
    private fun selectTab(tabType: String) {
        selectedTab = tabType
        
        when (tabType) {
            "info" -> {
                binding.infoTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.infoTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.recipesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.recipesTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
                
                binding.infoTabContent.visibility = android.view.View.VISIBLE
                binding.recipesTabContent.visibility = android.view.View.GONE
            }
            "recipes" -> {
                binding.recipesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.recipesTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.infoTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.infoTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
                
                binding.recipesTabContent.visibility = android.view.View.VISIBLE
                binding.infoTabContent.visibility = android.view.View.GONE
                
                repository.recipesLiveData.value?.let { recipes ->
                    setupRecipesTable(recipes)
                }
            }
        }
    }

    /**
     * Sets up the recipes table with recipes that use the current product.
     */
    private fun setupRecipesTable(recipes: List<Recipe>) {
        val currentProductId = currentProduct?.id
        if (currentProductId.isNullOrEmpty()) {
            showEmptyRecipesMessage("Sin recetas")
            return
        }

        val filteredRecipes = recipes.filter { recipe ->
            recipe.sections.any { section ->
                section.products.any { recipeProduct ->
                    recipeProduct.productId == currentProductId
                }
            }
        }

        if (filteredRecipes.isEmpty()) {
            showEmptyRecipesMessage("Este producto no se usa en ninguna receta")
            return
        }

        val sortedList = filteredRecipes.sortedBy { it.name }
        val columnConfigs = listOf("Nombre").toColumnConfigs()
        
        binding.recipesTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = RecipeTableAdapter(
                recipeList = sortedList,
                onRowClick = { recipe ->
                    openRecipeEditActivity(recipe)
                },
                onViewClick = { recipe ->
                    openRecipeEditActivity(recipe)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val recipe = item as Recipe
                when (columnIndex) {
                    0 -> recipe.name
                    else -> null
                }
            }
        )
    }

    /**
     * Shows an empty state message in the recipes table.
     */
    private fun showEmptyRecipesMessage(message: String) {
        binding.recipesTable.setupTableWithConfigs(
            columnConfigs = listOf("Nombre").toColumnConfigs(),
            data = listOf(EmptyRecipeItem(message)),
            adapter = EmptyRecipeAdapter(
                recipeList = listOf(EmptyRecipeItem(message)),
                onRowClick = { },
                onViewClick = { }
            ),
            pageSize = 10,
            columnValueGetter = { item, _ ->
                (item as EmptyRecipeItem).message
            }
        )
    }

    /**
     * Opens RecipeEditActivity with the selected recipe.
     */
    private fun openRecipeEditActivity(recipe: Recipe) {
        val intent = Intent(this, RecipeEditActivity::class.java)
        intent.putExtra("recipe", recipe)
        recipeEditActivityLauncher.launch(intent)
    }

    /**
     * Sets up the measure dropdown with the provided measures list.
     */
    private fun setupMeasureSpinner(measures: List<com.estaciondulce.app.models.Measure>) {
        val measureNames = measures.map { it.name }
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, measureNames)
        binding.measureDropdown.setAdapter(adapter)

        currentProduct?.measure?.let { productMeasureId ->
            val measure = measures.find { it.id == productMeasureId }
            binding.measureDropdown.setText(measure?.name ?: "", false)
        }

        binding.measureDropdown.setOnItemClickListener { _, _, _, _ ->
        }
    }


    /**
     * Checks if the product name is unique (ignoring case) using global products LiveData.
     */
    private fun isUniqueProductName(name: String, excludingId: String? = null): Boolean {
        return repository.productsLiveData.value?.none { product ->
            product.name.equals(name, ignoreCase = true) && product.id != excludingId
        } ?: true
    }

    /**
     * Validates the input fields and displays a CustomToast if validation fails.
     *
     * @return True if all inputs are valid.
     */
    private fun validateInputs(): Boolean {
        val productName = binding.productNameInput.text.toString().trim()
        if (productName.isEmpty()) {
            CustomToast.showError(this, "El nombre del producto es obligatorio.")
            return false
        }
        if (!isUniqueProductName(productName, currentProduct?.id)) {
            CustomToast.showError(this, "El nombre del producto ya existe.")
            return false
        }
        val stock = binding.productStockInput.text.toString().toDoubleOrNull() ?: -1.0
        if (stock < 0) {
            CustomToast.showError(this, "El stock no puede ser menor a 0.")
            return false
        }
        val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: -1.0
        if (cost < 0) {
            CustomToast.showError(this, "El costo no puede ser menor a 0.")
            return false
        }
        val salePrice = binding.productSalePriceInput.text.toString().toDoubleOrNull() ?: -1.0
        if (salePrice < 0) {
            CustomToast.showError(this, "El precio de venta no puede ser menor a 0.")
            return false
        }
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: -1.0
        if (minQty <= 0) {
            CustomToast.showError(this, "La cantidad mínima no puede ser menor a 0.")
            return false
        }
        val measureName = binding.measureDropdown.text.toString()
        val measureId = repository.measuresLiveData.value?.find { it.name == measureName }?.id
        if (measureId.isNullOrEmpty()) {
            CustomToast.showError(this, "Debe seleccionar una medida.")
            return false
        }
        return true
    }

    /**
     * Extracts a Product object from the input fields, rounding numeric values to two decimals.
     *
     * @return A Product object with values from the input fields.
     */
    private fun getProductFromInputs(): Product {
        val name = binding.productNameInput.text.toString().trim()
        val stock = binding.productStockInput.text.toString().toDoubleOrNull() ?: 0.0
        val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: 0.0
        val salePrice = binding.productSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: 0.0

        val roundedStock = Math.round(stock * 100.0) / 100.0
        val roundedCost = Math.round(cost * 100.0) / 100.0
        val roundedSalePrice = Math.round(salePrice * 100.0) / 100.0
        val roundedMinQty = Math.round(minQty * 100.0) / 100.0

        val measureName = binding.measureDropdown.text.toString()
        val measureId =
            repository.measuresLiveData.value?.find { it.name == measureName }?.id.orEmpty()

        return Product(
            id = currentProduct?.id ?: "",
            name = name,
            quantity = roundedStock,
            cost = roundedCost,
            salePrice = roundedSalePrice,
            minimumQuantity = roundedMinQty,
            measure = measureId
        )
    }

    /**
     * Saves the product by adding or updating it using ProductsHelper.
     */
    private fun saveProduct() {
        if (!validateInputs()) return
        val productToSave = getProductFromInputs()
        
        customLoader.show()
        
        if (currentProduct == null) {
            productsHelper.addProduct(
                product = productToSave,
                onSuccess = {
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Producto añadido correctamente.")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al añadir el producto: ${exception.message}")
                }
            )
        } else {
            productsHelper.updateProduct(
                productId = currentProduct!!.id,
                product = productToSave,
                onSuccess = {
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Producto actualizado correctamente.")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al actualizar el producto: ${exception.message}")
                }
            )
        }
    }
}

/**
 * Data class to represent an empty state message in the recipes table.
 */
data class EmptyRecipeItem(val message: String)

/**
 * Adapter for showing empty state messages in the recipes table.
 */
class EmptyRecipeAdapter(
    recipeList: List<EmptyRecipeItem>,
    onRowClick: (EmptyRecipeItem) -> Unit,
    onViewClick: (EmptyRecipeItem) -> Unit
) : TableAdapter<EmptyRecipeItem>(recipeList, onRowClick, { }) {

    private val onViewClickCallback = onViewClick

    override fun getCellValues(item: EmptyRecipeItem, position: Int): List<Any> {
        return listOf(item.message)
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: EmptyRecipeItem, position: Int) {
        bindRowContent(binding, getCellValues(item, position))
        
        binding.viewIcon.visibility = android.view.View.GONE
        binding.deleteIcon.visibility = android.view.View.GONE
        binding.actionIcon.visibility = android.view.View.GONE
        binding.mapsIcon.visibility = android.view.View.GONE
        
        configureIconSpacing(binding)
    }
}
