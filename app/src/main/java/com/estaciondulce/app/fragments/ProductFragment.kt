package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.ProductEditActivity
import com.estaciondulce.app.adapters.ProductAdapter
import com.estaciondulce.app.databinding.FragmentProductBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.models.toColumnConfigs
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment that displays the list of products.
 */
class ProductFragment : Fragment() {

    private var _binding: FragmentProductBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        _binding = FragmentProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Observes global LiveData for products and measures and sets up the table view.
     */
    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository.productsLiveData.observe(viewLifecycleOwner) { products ->
            setupTableView(products)
        }

        repository.measuresLiveData.observe(viewLifecycleOwner) {
            repository.productsLiveData.value?.let { products ->
                setupTableView(products)
            }
        }

        binding.addProductButton.setOnClickListener {
            openProductEditActivity(null)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterProducts(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Launches ProductEditActivity. If a product is provided, passes it for editing.
     */
    private fun openProductEditActivity(product: Product? = null) {
        val intent = Intent(requireContext(), ProductEditActivity::class.java)
        if (product != null) {
            intent.putExtra("PRODUCT", product)
        }
        productEditActivityLauncher.launch(intent)
    }

    /**
     * Launcher for ProductEditActivity.
     */
    private val productEditActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Global LiveData updates automatically.
        }
    }

    /**
     * Sets up the product table view using the given product list.
     */
    private fun setupTableView(products: List<Product>) {
        val sortedList = products.sortedBy { it.name }
        val columnConfigs = listOf("Nombre", "Stock", "Costo").toColumnConfigs(currencyColumns = setOf(2))
        binding.productTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
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
                    listOf(product.name, product.quantity, product.cost)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val product = item as Product
                when (columnIndex) {
                    0 -> product.name
                    1 -> product.quantity
                    2 -> product.cost
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
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = product.name,
            itemType = "producto",
            onConfirm = {
                ProductsHelper().deleteProduct(
                    productId = product.id,
                    onSuccess = {
                        Snackbar.make(
                            binding.root,
                            "Producto eliminado correctamente.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    },
                    onError = {
                        Snackbar.make(
                            binding.root,
                            "Error al eliminar el producto.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                )
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
