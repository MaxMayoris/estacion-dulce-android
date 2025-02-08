package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.ProductEditActivity
import com.estaciondulce.app.adapters.ProductAdapter
import com.estaciondulce.app.databinding.FragmentProductBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar

class ProductFragment : Fragment() {

    private var _binding: FragmentProductBinding? = null
    private val binding get() = _binding!!

    // Reference to the global FirestoreRepository
    private val repository = FirestoreRepository

    // ActivityResultLauncher for launching ProductEditActivity
    private val productEditActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // No need for explicit refresh because the global LiveData updates automatically.
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        _binding = FragmentProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe products LiveData to update the table whenever products change.
        repository.productsLiveData.observe(viewLifecycleOwner, Observer { products ->
            setupTableView(products)
        })

        // Observe measures LiveData in case measures change.
        repository.measuresLiveData.observe(viewLifecycleOwner, Observer {
            // Refresh table with current products if needed.
            repository.productsLiveData.value?.let { products ->
                setupTableView(products)
            }
        })

        binding.addProductButton.setOnClickListener {
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
     * If [product] is null, the activity is in "Add" mode.
     * If [product] is not null, it is passed as an extra for editing.
     * Since global listeners are used, there's no need to pass extra data.
     */
    private fun openProductEditActivity(product: Product? = null) {
        val intent = Intent(requireContext(), ProductEditActivity::class.java)
        if (product != null) {
            intent.putExtra("PRODUCT", product)
        }
        // With global listeners, ProductEditActivity can access the current data
        // via the repository's LiveData. No need to pass MEASURES_MAP or PRODUCTS_LIST.
        productEditActivityLauncher.launch(intent)
    }

    /**
     * Sets up the product table view using the provided product list.
     * It uses the measures LiveData to display the measure name.
     */
    private fun setupTableView(products: List<Product>) {
        val sortedList = products.sortedBy { it.name }
        // Retrieve the current list of measures from the repository's LiveData.
        val measuresList = repository.measuresLiveData.value ?: emptyList()

        binding.productTable.setupTable(
            columnHeaders = listOf("Nombre", "Stock", "Costo", "Unidad"),
            data = sortedList,
            adapter = ProductAdapter(
                productList = sortedList,
                onRowClick = { product ->
                    openProductEditActivity(product)
                },
                onDeleteClick = { product ->
                    deleteProduct(product)
                },
                attributeGetter = { product ->
                    // Match product.measure (the product's measure ID) with measure.id.
                    val measureName = measuresList.find { it.id == product.measure }?.name ?: "Desconocido"
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
                    3 -> measuresList.find { it.id == product.measure }?.name ?: "Desconocido"
                    else -> null
                }
            }
        )
    }

    /**
     * Filters the product list based on the search query and updates the table.
     */
    private fun filterProducts(query: String) {
        val products = repository.productsLiveData.value ?: emptyList()
        val filteredList = products.filter {
            it.name.contains(query, ignoreCase = true)
        }
        setupTableView(filteredList)
    }

    /**
     * Deletes a product using ProductsHelper.
     */
    private fun deleteProduct(product: Product) {
        // Call ProductsHelper directly; LiveData will update automatically.
        ProductsHelper().deleteProduct(
            productId = product.id,
            onSuccess = {
                Snackbar.make(binding.root, "Producto eliminado correctamente.",
                    Snackbar.LENGTH_LONG).show()
            },
            onError = { e ->
                Snackbar.make(binding.root, "Error al eliminar el producto.",
                    Snackbar.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
