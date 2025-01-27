package com.estaciondulce.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.estaciondulce.app.ProductBottomSheet
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize the loader
        loader = CustomLoader(requireContext())

        super.onViewCreated(view, savedInstanceState)

        loader.show()
        fetchData {
            setupTableView()
            loader.hide()
        }

        binding.addProductButton.setOnClickListener {
            ProductBottomSheet(onSave = { newProduct ->
                addProduct(newProduct)
            }).show(childFragmentManager, "AddProduct")
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterProducts(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun fetchData(onComplete: () -> Unit) {
        var productsLoaded = false
        var measuresLoaded = false

        fun checkAllDataLoaded() {
            if (productsLoaded && measuresLoaded) {
                onComplete()
            }
        }

        // Fetch measures
        measuresHelper.fetchMeasures(
            onSuccess = { measures ->
                measures.forEach { measure ->
                    measuresMap[measure.id] = measure
                }
                Log.d("ProductFragment", "Measures Loaded: $measuresMap")
                measuresLoaded = true
                checkAllDataLoaded()
            },
            onError = { e ->
                Log.e("ProductFragment", "Error fetching measures: ${e.message}")
                measuresLoaded = true // Allow progress even if fetching fails
                checkAllDataLoaded()
            }
        )

        // Fetch products
        productsHelper.fetchProducts(
            onSuccess = { products ->
                productList.clear()
                productList.addAll(products) // Add the List<Product> directly
                Log.d("ProductFragment", "Products Loaded: $productList")
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

    private fun addProduct(product: Product) {
        productsHelper.addProduct(
            product = product,
            onSuccess = { newProduct ->
                productList.add(newProduct)
                setupTableView() // Refresh the table
                Toast.makeText(requireContext(), "Producto añadido correctamente.", Toast.LENGTH_SHORT).show()
            },
            onError = { e ->
                Log.e("ProductFragment", "Error adding product: ${e.message}")
                Toast.makeText(requireContext(), "Error al añadir el producto.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun editProduct(product: Product) {
        ProductBottomSheet(product, onSave = { updatedProduct ->
            // Check if the new name already exists (excluding the current product)
            if (productList.any { it.name.equals(updatedProduct.name, ignoreCase = true) && it.id != product.id }) {
                showErrorDialog("A product with the name '${updatedProduct.name}' already exists.")
                return@ProductBottomSheet
            }

            productsHelper.updateProduct(
                productId = product.id,
                product = updatedProduct,
                onSuccess = {
                    val index = productList.indexOfFirst { it.id == product.id }
                    if (index != -1) {
                        productList[index] = updatedProduct.copy(id = product.id)
                        setupTableView()
                    }
                    Toast.makeText(requireContext(), "Producto actualizado correctamente.", Toast.LENGTH_SHORT).show()
                },
                onError = { e ->
                    Log.e("ProductFragment", "Error updating product: ${e.message}")
                    Toast.makeText(requireContext(), "Error al actualizar el producto.", Toast.LENGTH_SHORT).show()
                }
            )
        }).show(childFragmentManager, "EditProduct")
    }

    private fun deleteProduct(product: Product) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar el producto '${product.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                productsHelper.deleteProduct(
                    productId = product.id,
                    onSuccess = {
                        productList.remove(product)
                        setupTableView() // Refresh the table
                        Toast.makeText(requireContext(), "Producto eliminado correctamente.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { e ->
                        Log.e("ProductFragment", "Error deleting product: ${e.message}")
                        Toast.makeText(requireContext(), "Error al eliminar el producto.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
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
                onRowClick = { product -> editProduct(product) },
                onDeleteClick = { product -> deleteProduct(product) },
                attributeGetter = { product ->
                    val measureName = measuresMap[product.measure]?.name ?: "Desconocido" // Use name instead of unit
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
                    3 -> measuresMap[product.measure]?.name ?: "Desconocido" // Use name instead of unit
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
                onRowClick = { product -> editProduct(product) },
                onDeleteClick = { product -> deleteProduct(product) },
                attributeGetter = { product ->
                    val measureName = measuresMap[product.measure]?.name ?: "Desconocido" // Use name instead of unit
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
                    3 -> measuresMap[product.measure]?.name ?: "Desconocido" // Use name instead of unit
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
