package com.estaciondulce.app.activities

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityProductEditBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast

/**
 * Activity to add or update a product.
 */
class ProductEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductEditBinding
    private val productsHelper = ProductsHelper()
    private val repository = FirestoreRepository
    private var currentProduct: Product? = null
    private lateinit var customLoader: CustomLoader

    /**
     * Initializes the activity, sets up LiveData observers, and pre-populates fields if editing.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customLoader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        @Suppress("DEPRECATION")
        currentProduct = intent.getParcelableExtra<Product>("PRODUCT")
        supportActionBar?.title =
            if (currentProduct != null) "Editar Producto" else "Agregar Producto"

        repository.measuresLiveData.observe(this) { measures ->
            setupMeasureSpinner(measures)
        }

        currentProduct?.let { product ->
            binding.productNameInput.setText(product.name)
            binding.productStockInput.setText(product.quantity.toString())
            binding.productCostInput.setText(product.cost.toString())
            binding.productSalePriceInput.setText(product.salePrice.toString())
            binding.productMinimumQuantityInput.setText(product.minimumQuantity.toString())
        }


        binding.saveProductButton.setOnClickListener { saveProduct() }
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
