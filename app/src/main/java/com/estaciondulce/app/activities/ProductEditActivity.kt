package com.estaciondulce.app.activities

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityProductEditBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar

/**
 * Activity to add or update a product.
 */
class ProductEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductEditBinding
    private val productsHelper = ProductsHelper()
    private val recipesHelper = RecipesHelper()
    private val repository = FirestoreRepository
    private var currentProduct: Product? = null

    /**
     * Initializes the activity, sets up LiveData observers, and pre-populates fields if editing.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        currentProduct = intent.getParcelableExtra("PRODUCT")
        supportActionBar?.title =
            if (currentProduct != null) "Editar Producto" else "Agregar Producto"

        repository.measuresLiveData.observe(this) { measures ->
            setupMeasureSpinner(measures)
        }

        currentProduct?.let { product ->
            binding.productNameInput.setText(product.name)
            binding.productStockInput.setText(product.quantity.toString())
            binding.productCostInput.setText(product.cost.toString())
            binding.productMinimumQuantityInput.setText(product.minimumQuantity.toString())
        }

        binding.stockDecrementButton.setOnClickListener {
            adjustValue(
                binding.productStockInput,
                -1.0
            )
        }
        binding.stockIncrementButton.setOnClickListener {
            adjustValue(
                binding.productStockInput,
                1.0
            )
        }
        binding.minQtyDecrementButton.setOnClickListener {
            adjustValue(
                binding.productMinimumQuantityInput,
                -1.0
            )
        }
        binding.minQtyIncrementButton.setOnClickListener {
            adjustValue(
                binding.productMinimumQuantityInput,
                1.0
            )
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
     * Configures the measure spinner using the provided list of Measure objects.
     */
    private fun setupMeasureSpinner(measures: List<com.estaciondulce.app.models.Measure>) {
        val measureNames = measures.map { it.name }
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, measureNames)
        binding.measureDropdown.adapter = adapter

        currentProduct?.measure?.let { productMeasureId ->
            val measure = measures.find { it.id == productMeasureId }
            val position = measureNames.indexOf(measure?.name ?: "")
            if (position >= 0) binding.measureDropdown.setSelection(position)
        }

        binding.measureDropdown.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    /**
     * Adjusts the numeric value in the given EditText by a delta and rounds to two decimals.
     */
    private fun adjustValue(editText: EditText, delta: Double) {
        val currentVal = editText.text.toString().toDoubleOrNull() ?: 0.0
        val newVal = (currentVal + delta).coerceAtLeast(0.0)
        editText.setText(String.format("%.2f", newVal))
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
     * Validates the input fields and displays a Snackbar if validation fails.
     *
     * @return True if all inputs are valid.
     */
    private fun validateInputs(): Boolean {
        val productName = binding.productNameInput.text.toString().trim()
        if (productName.isEmpty()) {
            Snackbar.make(
                binding.root,
                "El nombre del producto es obligatorio.",
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }
        if (!isUniqueProductName(productName, currentProduct?.id)) {
            Snackbar.make(binding.root, "El nombre del producto ya existe.", Snackbar.LENGTH_LONG)
                .show()
            return false
        }
        val stock = binding.productStockInput.text.toString().toDoubleOrNull() ?: -1.0
        if (stock < 0) {
            Snackbar.make(binding.root, "El stock no puede ser menor a 0.", Snackbar.LENGTH_LONG)
                .show()
            return false
        }
        val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: -1.0
        if (cost <= 0) {
            Snackbar.make(binding.root, "El costo no puede ser menor a 0.", Snackbar.LENGTH_LONG)
                .show()
            return false
        }
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: -1.0
        if (minQty <= 0) {
            Snackbar.make(
                binding.root,
                "La cantidad mínima no puede ser menor a 0.",
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }
        val measureName = binding.measureDropdown.selectedItem?.toString()
        val measureId = repository.measuresLiveData.value?.find { it.name == measureName }?.id
        if (measureId.isNullOrEmpty()) {
            Snackbar.make(binding.root, "Debe seleccionar una medida.", Snackbar.LENGTH_LONG).show()
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
        val minQty = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: 0.0

        val roundedStock = Math.round(stock * 100.0) / 100.0
        val roundedCost = Math.round(cost * 100.0) / 100.0
        val roundedMinQty = Math.round(minQty * 100.0) / 100.0

        val measureName = binding.measureDropdown.selectedItem?.toString()
        val measureId =
            repository.measuresLiveData.value?.find { it.name == measureName }?.id.orEmpty()

        return Product(
            id = currentProduct?.id ?: "",
            name = name,
            quantity = roundedStock,
            cost = roundedCost,
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
        if (currentProduct == null) {
            productsHelper.addProduct(
                product = productToSave,
                onSuccess = {
                    Snackbar.make(
                        binding.root,
                        "Producto añadido correctamente.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = {
                    Snackbar.make(
                        binding.root,
                        "Error al añadir el producto.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            )
        } else {
            productsHelper.updateProduct(
                productId = currentProduct!!.id,
                product = productToSave,
                onSuccess = {
                    RecipesHelper().updateCascadeAffectedRecipesFromProduct(
                        currentProduct!!.id,
                        onComplete = {
                            Snackbar.make(
                                binding.root,
                                "Producto actualizado correctamente.",
                                Snackbar.LENGTH_LONG
                            ).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onError = { exception ->
                            Snackbar.make(
                                binding.root,
                                "Error en actualización en cascada: ${exception.message}",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    )
                },
                onError = {
                    Snackbar.make(
                        binding.root,
                        "Error al actualizar el producto.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}
