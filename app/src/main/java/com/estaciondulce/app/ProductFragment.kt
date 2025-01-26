package com.estaciondulce.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.estaciondulce.app.ProductBottomSheet
import com.estaciondulce.app.adapters.ProductAdapter
import com.estaciondulce.app.databinding.FragmentProductBinding
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductFragment : Fragment() {

    private var _binding: FragmentProductBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private val productList = mutableListOf<Product>()
    private val measuresMap = mutableMapOf<String, Measure>() // Map to store measures by key

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchMeasures { fetchProducts() }

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

    private fun fetchMeasures(onComplete: () -> Unit) {
        db.collection("measures")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val measure = document.toObject(Measure::class.java)
                    measuresMap[document.id] = measure // Map measure key to Measure object
                }
                onComplete()
            }
    }

    private fun fetchProducts() {
        db.collection("products")
            .get()
            .addOnSuccessListener { documents ->
                productList.clear()
                for (document in documents) {
                    val product = document.toObject(Product::class.java).copy(id = document.id)
                    productList.add(product)
                }
                setupTableView()
            }
            .addOnFailureListener { e ->
                Log.e("ProductFragment", "Error fetching products: ${e.message}")
            }
    }


    private fun addProduct(product: Product) {
        // Check if the product name already exists
        if (productList.any { it.name.equals(product.name, ignoreCase = true) }) {
            showErrorDialog("A product with the name '${product.name}' already exists.")
            return
        }

        val productData = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "cost" to product.cost,
            "measure" to product.measure,
            "minimumQuantity" to product.minimumQuantity
        )
        db.collection("products")
            .add(productData)
            .addOnSuccessListener { documentReference ->
                val productWithId = product.copy(id = documentReference.id)
                productList.add(productWithId)
                setupTableView() // Refresh the table
            }
            .addOnFailureListener { e ->
                Log.e("ProductFragment", "Error adding product: ${e.message}")
            }
    }


    private fun editProduct(product: Product) {
        ProductBottomSheet(product, onSave = { updatedProduct ->
            // Check if the new name already exists (excluding the current product)
            if (productList.any { it.name.equals(updatedProduct.name, ignoreCase = true) && it.id != product.id }) {
                showErrorDialog("A product with the name '${updatedProduct.name}' already exists.")
                return@ProductBottomSheet
            }

            val productData = mapOf(
                "name" to updatedProduct.name,
                "quantity" to updatedProduct.quantity,
                "cost" to updatedProduct.cost,
                "measure" to updatedProduct.measure,
                "minimumQuantity" to updatedProduct.minimumQuantity
            )
            db.collection("products")
                .document(product.id) // Use Firestore document ID
                .set(productData)
                .addOnSuccessListener {
                    val index = productList.indexOfFirst { it.id == product.id }
                    if (index != -1) {
                        productList[index] = updatedProduct.copy(id = product.id)
                        setupTableView() // Refresh the table
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProductFragment", "Error updating product: ${e.message}")
                }
        }).show(childFragmentManager, "EditProduct")
    }


    private fun deleteProduct(product: Product) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete the product '${product.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                // Proceed with deletion
                db.collection("products")
                    .document(product.id)
                    .delete()
                    .addOnSuccessListener {
                        productList.remove(product)
                        setupTableView() // Refresh the table
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProductFragment", "Error deleting product: ${e.message}")
                    }
            }
            .setNegativeButton("Cancel", null) // Do nothing on cancel
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
                    val measure = measuresMap[product.measure]?.unit ?: "Unknown"
                    listOf(product.name, product.quantity, product.cost, measure)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val product = item as Product // Explicitly cast to Product
                when (columnIndex) {
                    0 -> product.name
                    1 -> product.quantity
                    2 -> product.cost
                    3 -> measuresMap[product.measure]?.unit ?: "Unknown"
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
                    val measure = measuresMap[product.measure]?.unit ?: "Unknown"
                    listOf(product.name, product.quantity, product.cost, measure)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val product = item as Product // Explicitly cast to Product
                when (columnIndex) {
                    0 -> product.name
                    1 -> product.quantity
                    2 -> product.cost
                    3 -> measuresMap[product.measure]?.unit ?: "Unknown"
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
