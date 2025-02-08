package com.estaciondulce.app

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityProductEditBinding
import com.estaciondulce.app.helpers.MeasuresHelper
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.Product
import com.google.android.material.snackbar.Snackbar

class ProductEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductEditBinding
    private val productsHelper = ProductsHelper()
    private val measuresHelper = MeasuresHelper()
    private val measuresListMap = mutableMapOf<String, String>()
    private var productsListMap: HashMap<String, String> = hashMapOf()
    private var currentProduct: Product? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        currentProduct = intent.getParcelableExtra("PRODUCT")
        supportActionBar?.title = if (currentProduct != null) "Editar Producto" else "Agregar Producto"

        // Attempt to receive MEASURES_MAP from the fragment; otherwise, fetch via helper.
        val passedMeasures = intent.getSerializableExtra("MEASURES_MAP") as? Map<String, String>
        if (passedMeasures != null) {
            measuresListMap.putAll(passedMeasures)
            setupMeasureSpinner()
        } else {
            fetchMeasures()
        }
        // Receive the products list (only id and name) from the fragment.
        val passedProductsMap = intent.getSerializableExtra("PRODUCTS_LIST") as? HashMap<String, String>
        if (passedProductsMap != null) {
            productsListMap.putAll(passedProductsMap)
        }

        currentProduct?.let { product ->
            binding.productNameInput.setText(product.name)
            binding.productStockInput.setText(product.quantity.toString())
            binding.productCostInput.setText(product.cost.toString())
            binding.productMinimumQuantityInput.setText(product.minimumQuantity.toString())
        }

        // Set listeners for the increment/decrement buttons.
        binding.stockDecrementButton.setOnClickListener { adjustValue(binding.productStockInput, -1.0) }
        binding.stockIncrementButton.setOnClickListener { adjustValue(binding.productStockInput, 1.0) }
        binding.minQtyDecrementButton.setOnClickListener { adjustValue(binding.productMinimumQuantityInput, -1.0) }
        binding.minQtyIncrementButton.setOnClickListener { adjustValue(binding.productMinimumQuantityInput, 1.0) }

        binding.saveProductButton.setOnClickListener { saveProduct() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Fetches the list of measures using the MeasuresHelper.
    private fun fetchMeasures() {
        measuresHelper.fetchMeasures(
            onSuccess = { measuresList ->
                for (measure in measuresList) {
                    measuresListMap[measure.id] = measure.name
                }
                setupMeasureSpinner()
            },
            onError = {
                Snackbar.make(binding.root, "Error al obtener las medidas.", Snackbar.LENGTH_LONG).show()
            }
        )
    }

    // Sets up the spinner with the list of measures.
    private fun setupMeasureSpinner() {
        val measureNames = measuresListMap.values.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, measureNames)
        binding.measureDropdown.adapter = adapter

        currentProduct?.measure?.let { measureId ->
            val measureName = measuresListMap[measureId]
            val position = measureNames.indexOf(measureName)
            if (position != -1) {
                binding.measureDropdown.setSelection(position)
            }
        }

        binding.measureDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // No additional action required when a measure is selected.
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }

    // Helper function to adjust the value of an EditText by a given delta.
    private fun adjustValue(editText: EditText, delta: Double) {
        val currentVal = editText.text.toString().toDoubleOrNull() ?: 0.0
        val newVal = (currentVal + delta).coerceAtLeast(0.0)
        editText.setText(String.format("%.3f", newVal))
    }

    // Checks if the product name is unique (case-insensitive), excluding a given product ID if provided.
    private fun isUniqueProductName(name: String, excludingId: String? = null): Boolean {
        return productsListMap.none { (id, productName) ->
            productName.equals(name, ignoreCase = true) && id != excludingId
        }
    }

    // Validates the input fields and shows a Snackbar if any validation fails.
    private fun validateInputs(): Boolean {
        val productName = binding.productNameInput.text.toString().trim()
        if (productName.isEmpty()) {
            Snackbar.make(binding.root, "El nombre del producto es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }
        if (!isUniqueProductName(productName, currentProduct?.id)) {
            Snackbar.make(binding.root, "El nombre del producto ya existe.", Snackbar.LENGTH_LONG).show()
            return false
        }
        val stock = binding.productStockInput.text.toString().toDoubleOrNull() ?: -1.0
        if (stock < 0) {
            Snackbar.make(binding.root, "El stock no puede ser menor a 0.", Snackbar.LENGTH_LONG).show()
            return false
        }
        val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: -1.0
        if (cost <= 0) {
            Snackbar.make(binding.root, "El costo no puede ser menor a 0.", Snackbar.LENGTH_LONG).show()
            return false
        }
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: -1.0
        if (minQty <= 0) {
            Snackbar.make(binding.root, "La cantidad mínima no puede ser menor a 0.", Snackbar.LENGTH_LONG).show()
            return false
        }
        val measureName = binding.measureDropdown.selectedItem?.toString()
        val measureId = measuresListMap.entries.find { it.value == measureName }?.key
        if (measureId.isNullOrEmpty()) {
            Snackbar.make(binding.root, "Debe seleccionar una medida.", Snackbar.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // Extracts a Product object from the input fields.
    private fun getProductFromInputs(): Product {
        val name = binding.productNameInput.text.toString().trim()
        val stock = binding.productStockInput.text.toString().toDoubleOrNull() ?: 0.0
        val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: 0.0
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: 0.0
        val measureName = binding.measureDropdown.selectedItem?.toString()
        val measureId = measuresListMap.entries.find { it.value == measureName }?.key.orEmpty()
        return Product(
            id = currentProduct?.id ?: "",
            name = name,
            quantity = stock,
            cost = cost,
            minimumQuantity = minQty,
            measure = measureId
        )
    }

    // Saves the product by adding a new product or updating an existing one.
    private fun saveProduct() {
        if (!validateInputs()) return
        val productToSave = getProductFromInputs()
        if (currentProduct == null) {
            productsHelper.addProduct(
                product = productToSave,
                onSuccess = {
                    Snackbar.make(binding.root, "Producto añadido correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = {
                    Snackbar.make(binding.root, "Error al añadir el producto.", Snackbar.LENGTH_LONG).show()
                }
            )
        } else {
            productsHelper.updateProduct(
                productId = currentProduct!!.id,
                product = productToSave,
                onSuccess = {
                    Snackbar.make(binding.root, "Producto actualizado correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = {
                    Snackbar.make(binding.root, "Error al actualizar el producto.", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }
}
