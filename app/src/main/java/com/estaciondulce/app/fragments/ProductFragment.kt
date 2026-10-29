package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.ProductEditActivity
import com.estaciondulce.app.adapters.ProductAdapter
import com.estaciondulce.app.databinding.FragmentProductBinding
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.ConfirmationDialog
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.models.toColumnConfigs
import com.google.android.material.snackbar.Snackbar

/**
 * Fragment that displays the list of products.
 */
class ProductFragment : Fragment() {

    private var _binding: FragmentProductBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private var lowStockOnly = false

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

        repository.productsLiveData.observe(viewLifecycleOwner) {
            updateLowStockBanner()
            applyFilters()
        }

        repository.measuresLiveData.observe(viewLifecycleOwner) {
            applyFilters()
        }

        binding.lowStockBanner.setOnClickListener {
            lowStockOnly = !lowStockOnly
            updateLowStockBanner()
            applyFilters()
        }

        binding.addProductButton.setOnClickListener {
            openProductEditActivity(null)
        }

        binding.resetStockButton.setOnClickListener {
            confirmResetAllStocks()
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
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
     * Shows a banner with the number of products below their minimum stock (hidden when there are none).
     * Its look and text reflect whether the list is currently filtered to those products.
     */
    private fun updateLowStockBanner() {
        val lowStockCount = (repository.productsLiveData.value ?: emptyList()).count { isBelowMinimum(it) }
        if (lowStockCount == 0) {
            lowStockOnly = false
            binding.lowStockBanner.visibility = android.view.View.GONE
            return
        }
        val context = requireContext()
        val productsLabel = if (lowStockCount == 1) "1 producto" else "$lowStockCount productos"
        val backgroundColor = if (lowStockOnly) R.color.warning_orange_dark else R.color.warning_orange_light
        val contentColor = ContextCompat.getColor(context, if (lowStockOnly) R.color.white else R.color.warning_orange_text)
        binding.lowStockBanner.visibility = android.view.View.VISIBLE
        binding.lowStockBanner.setCardBackgroundColor(ContextCompat.getColor(context, backgroundColor))
        binding.lowStockText.text = if (lowStockOnly) "Mostrando $productsLabel bajo el mínimo" else "$productsLabel bajo el stock mínimo"
        binding.lowStockAction.text = if (lowStockOnly) "Ver todos" else "Ver"
        binding.lowStockText.setTextColor(contentColor)
        binding.lowStockAction.setTextColor(contentColor)
        binding.lowStockIcon.imageTintList = ColorStateList.valueOf(contentColor)
    }

    /**
     * Filters the product list by the search query and, when enabled, by stock below the minimum.
     */
    private fun applyFilters() {
        val query = binding.searchBar.text.toString()
        val filteredList = (repository.productsLiveData.value ?: emptyList()).filter {
            it.name.contains(query, ignoreCase = true) && (!lowStockOnly || isBelowMinimum(it))
        }
        setupTableView(filteredList)
    }

    private fun isBelowMinimum(product: Product): Boolean = product.quantity < product.minimumQuantity

    /**
     * Deletes a product using ProductsHelper with validation.
     */
    private fun deleteProduct(product: Product) {
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = product.name,
            itemType = "producto",
            onConfirm = {
                val recipes = repository.recipesLiveData.value ?: emptyList()
                val isProductInRecipes = recipes.any { recipe ->
                    recipe.sections.any { section ->
                        section.products.any { recipeProduct ->
                            recipeProduct.productId == product.id
                        }
                    }
                }
                
                if (isProductInRecipes) {
                    CustomToast.showError(requireContext(), "No se puede eliminar el producto '${product.name}' porque está siendo usado en una o más recetas.")
                    return@show
                }
                
                val loader = CustomLoader(requireContext())
                loader.show()
                
                ProductsHelper().deleteProduct(
                    productId = product.id,
                    onSuccess = {
                        loader.hide()
                        CustomToast.showSuccess(requireContext(), "Producto '${product.name}' eliminado correctamente.")
                    },
                    onError = { exception ->
                        loader.hide()
                        CustomToast.showError(requireContext(), "Error al eliminar el producto: ${exception.message}")
                    }
                )
            }
        )
    }

    /**
     * Asks for confirmation and then sets the stock of every product with non-zero stock to 0.
     */
    private fun confirmResetAllStocks() {
        val productsToReset = (repository.productsLiveData.value ?: emptyList()).filter { it.quantity != 0.0 }
        if (productsToReset.isEmpty()) {
            CustomToast.showInfo(requireContext(), "Todos los productos ya tienen stock en 0.")
            return
        }
        val productsLabel = if (productsToReset.size == 1) "1 producto" else "${productsToReset.size} productos"
        ConfirmationDialog.show(
            context = requireContext(),
            title = "Poner stock en 0",
            message = "Se va a poner en 0 el stock de $productsLabel. Esta acción no se puede deshacer.\n\n¿Querés continuar?",
            confirmButtonText = "Poner en 0",
            onConfirm = {
                val loader = CustomLoader(requireContext())
                loader.show()
                ProductsHelper().resetStocks(
                    products = productsToReset,
                    onSuccess = {
                        loader.hide()
                        CustomToast.showSuccess(requireContext(), "Stock de $productsLabel puesto en 0.")
                    },
                    onError = { exception ->
                        loader.hide()
                        CustomToast.showError(requireContext(), "Error al poner el stock en 0: ${exception.message}")
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
