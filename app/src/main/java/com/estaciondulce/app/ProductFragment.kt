package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.estaciondulce.app.ProductEditActivity
import com.estaciondulce.app.adapters.ProductAdapter
import com.estaciondulce.app.databinding.FragmentProductBinding
import com.estaciondulce.app.helpers.MeasuresHelper
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.utils.CustomLoader

class ProductFragment : Fragment() {

    private var _binding: FragmentProductBinding? = null
    private val binding get() = _binding!!

    private val productsHelper = ProductsHelper()
    private val measuresHelper = MeasuresHelper()

    private val productList = mutableListOf<Product>()
    private val measuresMap = mutableMapOf<String, Measure>()
    private lateinit var loader: CustomLoader

    // Register the ActivityResultLauncher to launch ProductEditActivity and receive a result
    private val productEditActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("ProductFragment", "ProductEditActivity result code: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            // Refresh data after adding or editing a product
            Log.d("ProductFragment", "Refreshing product data after successful edit/add.")
            fetchData {
                setupTableView()
            }
        } else {
            Log.e("ProductFragment", "ProductEditActivity did not return RESULT_OK")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loader = CustomLoader(requireContext())
        super.onViewCreated(view, savedInstanceState)

        loader.show()
        fetchData {
            setupTableView()
            loader.hide()
        }

        // When clicking the add button, launch ProductEditActivity in "Add" mode
        binding.addProductButton.setOnClickListener {
            Log.d("ProductFragment", "Add button clicked. Launching ProductEditActivity in Add mode.")
            openProductEditActivity(null)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterProducts(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })
    }

    /**
     * Launches ProductEditActivity.
     * If [product] is null, the activity will be in "Add" mode.
     * If [product] is not null, it will be passed as an extra for editing.
     */
    private fun openProductEditActivity(product: Product? = null) {
        val intent = Intent(requireContext(), ProductEditActivity::class.java)
        if (product != null) {
            intent.putExtra("PRODUCT", product)
        }
        // Convert measuresMap (Map<String, Measure>) to Map<String, String> (id -> name)
        val measuresStringMap = measuresMap.mapValues { it.value.name }
        intent.putExtra("MEASURES_MAP", HashMap(measuresStringMap))
        // Convert productList to a map of id -> name
        val productsNamesMap = HashMap(productList.associate { it.id to it.name })
        intent.putExtra("PRODUCTS_LIST", productsNamesMap)
        productEditActivityLauncher.launch(intent)
    }

    private fun fetchData(onComplete: () -> Unit) {
        var productsLoaded = false
        var measuresLoaded = false

        fun checkAllDataLoaded() {
            if (productsLoaded && measuresLoaded) {
                onComplete()
            }
        }

        // Fetch measures from Firestore or your data source
        measuresHelper.fetchMeasures(
            onSuccess = { measures ->
                measures.forEach { measure ->
                    measuresMap[measure.id] = measure
                }
                Log.d("ProductFragment", "Measures loaded: $measuresMap")
                measuresLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("ProductFragment", "Error fetching measures: ${e.message}")
                measuresLoaded = true
                checkAllDataLoaded()
            }
        )

        // Fetch products from Firestore or your data source
        productsHelper.fetchProducts(
            onSuccess = { products ->
                productList.clear()
                productList.addAll(products)
                Log.d("ProductFragment", "Products loaded: $productList")
                productsLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("ProductFragment", "Error fetching products: ${e.message}")
                productsLoaded = true
                checkAllDataLoaded()
            }
        )
    }

    private fun deleteProduct(product: Product) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete the product '${product.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                productsHelper.deleteProduct(
                    productId = product.id,
                    onSuccess = {
                        productList.remove(product)
                        setupTableView()
                        Toast.makeText(
                            requireContext(),
                            "Producto eliminado correctamente.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { e ->
                        Log.e("ProductFragment", "Error deleting product: ${e.message}")
                        Toast.makeText(
                            requireContext(),
                            "Error al eliminar el producto.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun showErrorDialog(message: String) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
        dialog.show()
    }

    private fun setupTableView() {
        val sortedList = productList.sortedBy { it.name }
        binding.productTable.setupTable(
            columnHeaders = listOf("Nombre", "Stock", "Costo", "Unidad"),
            data = sortedList,
            adapter = ProductAdapter(
                productList = sortedList,
                // When clicking a row, launch ProductEditActivity in "Edit" mode
                onRowClick = { product ->
                    Log.d("ProductFragment", "Row clicked. Editing product with id: ${product.id}")
                    openProductEditActivity(product)
                },
                onDeleteClick = { product ->
                    deleteProduct(product)
                },
                attributeGetter = { product ->
                    val measureName = measuresMap[product.measure]?.name ?: "Desconocido"
                    listOf(product.name, product.quantity, product.cost, measureName)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val product = item as Product
                when (columnIndex) {
                    0 -> product.name
                    1 -> product.quantity
                    2 -> product.cost
                    3 -> measuresMap[product.measure]?.name ?: "Desconocido"
                    else -> null
                }
            }
        )
    }

    private fun filterProducts(query: String) {
        val filteredList = productList.filter {
            it.name.contains(query, ignoreCase = true)
        }
        binding.productTable.setupTable(
            columnHeaders = listOf("Nombre", "Stock", "Costo", "Unidad"),
            data = filteredList,
            adapter = ProductAdapter(
                productList = filteredList,
                onRowClick = { product ->
                    openProductEditActivity(product)
                },
                onDeleteClick = { product ->
                    deleteProduct(product)
                },
                attributeGetter = { product ->
                    val measureName = measuresMap[product.measure]?.name ?: "Desconocido"
                    listOf(product.name, product.quantity, product.cost, measureName)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val product = item as Product
                when (columnIndex) {
                    0 -> product.name
                    1 -> product.quantity
                    2 -> product.cost
                    3 -> measuresMap[product.measure]?.name ?: "Desconocido"
                    else -> null
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
