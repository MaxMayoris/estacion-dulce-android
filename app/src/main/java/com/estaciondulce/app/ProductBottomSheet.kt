package com.estaciondulce.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.estaciondulce.app.databinding.BottomSheetProductBinding
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductBottomSheet(
    private val product: Product? = null, // Pass null for "Add"
    private val onSave: (Product) -> Unit // Callback for saving the product
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetProductBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val measuresMap = mutableMapOf<String, String>() // Map of measure ID to name

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFloatingLabels() // Setup floating labels

        // Fetch measures and populate dropdown
        fetchMeasures { measureNames ->
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                measureNames
            )
            binding.measureDropdown.adapter = adapter

            // Preselect the measure if editing
            product?.measure?.let { measureId ->
                val measureName = measuresMap[measureId]
                val position = measureNames.indexOf(measureName)
                if (position != -1) {
                    binding.measureDropdown.setSelection(position)
                }
            }
        }

        // Prepopulate fields for editing
        product?.let {
            binding.productNameInput.setText(it.name)
            binding.productStockInput.setText(it.quantity.toString())
            binding.productCostInput.setText(it.cost.toString())
            binding.productMinimumQuantityInput.setText(it.minimumQuantity.toString())
        }

        // Save button logic
        binding.saveProductButton.setOnClickListener {
            val name = binding.productNameInput.text.toString()
            val stock = binding.productStockInput.text.toString().toIntOrNull() ?: 0
            val cost = binding.productCostInput.text.toString().toDoubleOrNull() ?: 0.0
            val minimumQuantity = binding.productMinimumQuantityInput.text.toString().toDoubleOrNull() ?: 0.0
            val measureName = binding.measureDropdown.selectedItem?.toString()
            val measureId = measuresMap.entries.find { it.value == measureName }?.key

            val updatedProduct = Product(
                id = product?.id ?: "", // Keep existing ID if editing
                name = name,
                quantity = stock,
                cost = cost,
                minimumQuantity = minimumQuantity,
                measure = measureId ?: "" // Save the measure ID
            )
            onSave(updatedProduct)
            dismiss()
        }
    }

    private fun setupFloatingLabels() {
        binding.productNameInput.addTextChangedListener { text ->
            binding.productNameLabel.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.productStockInput.addTextChangedListener { text ->
            binding.productStockLabel.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.productCostInput.addTextChangedListener { text ->
            binding.productCostLabel.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.productMinimumQuantityInput.addTextChangedListener { text ->
            binding.productMinimumQuantityLabel.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.measureDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.measureLabel.visibility = if (position != -1) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.measureLabel.visibility = View.GONE
            }
        }

    }

    private fun fetchMeasures(onComplete: (List<String>) -> Unit) {
        db.collection("measures")
            .get()
            .addOnSuccessListener { documents ->
                val measureNames = mutableListOf<String>()
                for (document in documents) {
                    val measure = document.toObject(Measure::class.java)
                    measureNames.add(measure.name)
                    measuresMap[document.id] = measure.name // Map ID to name
                }
                onComplete(measureNames)
            }
            .addOnFailureListener {
                onComplete(emptyList()) // Handle failure gracefully
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
