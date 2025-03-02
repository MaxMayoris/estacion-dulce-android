package com.estaciondulce.app.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.adapters.MovementItemsAdapter
import com.estaciondulce.app.databinding.ActivityMovementEditBinding
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.helpers.ProductsHelper
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.Address
import com.estaciondulce.app.models.EMovementType
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.MovementItem
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.models.Shipment
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ItemEntry(val name: String, val collection: String, val collectionId: String) {
    override fun toString(): String = name
}

/**
 * Activity for adding or editing a movement.
 */
class MovementEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementEditBinding
    private val movementsHelper = MovementsHelper()
    private var currentMovement: Movement? = null
    private val repository = FirestoreRepository
    private var selectedDate: Date = Date()
    private var personsList: List<Person> = listOf()
    private var movementItems: MutableList<MovementItem> = mutableListOf()
    private lateinit var itemsAdapter: MovementItemsAdapter
    private var originalProductItems: List<MovementItem> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        currentMovement = intent.getParcelableExtra("MOVEMENT")
        supportActionBar?.title =
            if (currentMovement != null) "Editar Movimiento" else "Agregar Movimiento"
        binding.dateInput.setOnClickListener { showDatePickerDialog() }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemsAdapter = MovementItemsAdapter(
            movementItems,
            onItemChanged = { recalcTotalAmount() },
            onDeleteClicked = { position ->
                movementItems.removeAt(position)
                itemsAdapter.updateItems(movementItems)
                recalcTotalAmount()
            },
            getDisplayName = { collection, collectionId ->
                when (collection) {
                    "products" -> repository.productsLiveData.value?.find { it.id == collectionId }?.name
                        ?: "Desconocido"

                    "recipes" -> repository.recipesLiveData.value?.find { it.id == collectionId }?.name
                        ?: "Desconocido"

                    "custom" -> movementItems.find { it.collection == "custom" && it.customName != null }?.customName
                        ?: "Personalizado"

                    else -> "Desconocido"
                }
            }
        )
        binding.itemsRecyclerView.adapter = itemsAdapter
        binding.addItemButton.setOnClickListener { showAddItemDialog() }
        repository.personsLiveData.observe(this) { persons ->
            personsList = persons
            setupPersonSpinner(persons)
            currentMovement?.let { movement ->
                val index = persons.indexOfFirst { it.id == movement.personId }
                if (index != -1) binding.personSpinner.setSelection(index)
            }
        }
        setupMovementTypeSpinner()
        currentMovement?.let { movement ->
            selectedDate = movement.date
            binding.dateInput.setText(formatDate(movement.date))
            binding.totalAmountInput.setText(movement.totalAmount.toString())
            val movementTypeIndex = when (movement.type) {
                EMovementType.PURCHASE -> 0
                EMovementType.SALE -> 1
                else -> 0
            }
            binding.movementTypeSpinner.setSelection(movementTypeIndex)
            if (movement.type == EMovementType.SALE) {
                binding.shippingRow.visibility = View.VISIBLE
                val shippingCost = movement.shipment?.shippingCost ?: 0.0
                binding.shippingCostInput.setText(shippingCost.toString())
                binding.shippingCheckBox.isChecked = shippingCost != 0.0
                if (binding.shippingCheckBox.isChecked) {
                    binding.shippingCostLabel.visibility = View.VISIBLE
                    binding.shippingCostInput.visibility = View.VISIBLE
                    binding.shippingAddressLabel.visibility = View.VISIBLE
                    binding.shippingAddressInput.visibility = View.VISIBLE
                    if (movement.shipment?.addressId?.isNotEmpty() == true) {
                        val address =
                            repository.addressesLiveData.value?.find { it.id == movement.shipment.addressId }
                        binding.shippingAddressInput.setText(address?.rawAddress ?: "")
                    }
                } else {
                    binding.shippingCostLabel.visibility = View.GONE
                    binding.shippingCostInput.visibility = View.GONE
                    binding.shippingAddressLabel.visibility = View.GONE
                    binding.shippingAddressInput.visibility = View.GONE
                }
            } else {
                binding.shippingRow.visibility = View.GONE
            }
            movementItems.clear()
            movementItems.addAll(movement.items)
            itemsAdapter.updateItems(movementItems)
            recalcTotalAmount()
            originalProductItems =
                movement.items.filter { it.collection == "products" }.map { it.copy() }
        } ?: run {
            binding.dateInput.setText(formatDate(selectedDate))
        }
        binding.movementTypeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 1) {
                        binding.shippingRow.visibility = View.VISIBLE
                    } else {
                        binding.shippingRow.visibility = View.GONE
                        binding.shippingCostInput.setText("0.0")
                        binding.shippingAddressInput.setText("")
                        binding.shippingCheckBox.isChecked = false
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    binding.shippingRow.visibility = View.GONE
                    binding.shippingCostInput.setText("0.0")
                    binding.shippingAddressInput.setText("")
                    binding.shippingCheckBox.isChecked = false
                }
            }
        binding.shippingCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.shippingCostLabel.visibility = View.VISIBLE
                binding.shippingCostInput.visibility = View.VISIBLE
                binding.shippingAddressLabel.visibility = View.VISIBLE
                binding.shippingAddressInput.visibility = View.VISIBLE
            } else {
                binding.shippingCostLabel.visibility = View.GONE
                binding.shippingCostInput.visibility = View.GONE
                binding.shippingAddressLabel.visibility = View.GONE
                binding.shippingAddressInput.visibility = View.GONE
            }
        }
        binding.shippingCostInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                recalcTotalAmount()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.personSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedPersonId = personsList[position].id
                if (binding.shippingRow.visibility == View.VISIBLE && binding.shippingCheckBox.isChecked) {
                    val address = repository.addressesLiveData.value?.find { it.personId == selectedPersonId }
                    binding.shippingAddressInput.setText(address?.rawAddress ?: "")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }
        binding.saveMovementButton.setOnClickListener { saveMovement() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Displays a dialog to add a custom item.
     */
    private fun showCustomItemDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Agregar ítem personalizado")
        val input = android.widget.EditText(this)
        input.hint = "Nombre del ítem"
        builder.setView(input)
        builder.setPositiveButton("Agregar") { dialog, _ ->
            val customName = input.text.toString().trim()
            if (customName.isNotEmpty()) {
                val newItem = MovementItem("custom", "", customName, 1.0, 1.0)
                movementItems.add(newItem)
                itemsAdapter.updateItems(movementItems)
                recalcTotalAmount()
            } else {
                Snackbar.make(binding.root, "El nombre no puede estar vacío.", Snackbar.LENGTH_LONG)
                    .show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    /**
     * Sets up the person spinner with the provided list.
     */
    private fun setupPersonSpinner(persons: List<Person>) {
        val personNames = persons.map { "${it.name} ${it.lastName}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, personNames)
        binding.personSpinner.adapter = adapter
    }

    /**
     * Sets up the movement type spinner with "Compra" and "Venta" options.
     */
    private fun setupMovementTypeSpinner() {
        val movementTypes = listOf("Compra", "Venta")
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, movementTypes)
        binding.movementTypeSpinner.adapter = adapter
    }

    /**
     * Displays a DatePickerDialog to select a date.
     */
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance().apply { time = selectedDate }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            selectedDate = calendar.time
            binding.dateInput.setText(formatDate(selectedDate))
        }, year, month, day).show()
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date)
    }

    /**
     * Recalculates the total amount based on item cost, quantity and shipping cost if shipping is enabled.
     */
    private fun recalcTotalAmount() {
        val itemsTotal = movementItems.sumOf { it.cost * it.quantity }
        val shippingCost =
            if (binding.shippingRow.visibility == View.VISIBLE && binding.shippingCheckBox.isChecked) {
                binding.shippingCostInput.text.toString().toDoubleOrNull() ?: 0.0
            } else 0.0
        val total = itemsTotal + shippingCost
        binding.totalAmountInput.setText(total.toString())
        binding.itemsHeader.visibility = if (movementItems.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Displays a dialog for adding an item by searching products and recipes.
     */
    private fun showAddItemDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val dialogView =
            layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_item, null)
        builder.setView(dialogView)
        val searchEditText =
            dialogView.findViewById<android.widget.EditText>(com.estaciondulce.app.R.id.searchEditText)
        val itemsListView =
            dialogView.findViewById<android.widget.ListView>(com.estaciondulce.app.R.id.itemsListView)
        val products = repository.productsLiveData.value ?: emptyList()
        val recipes = repository.recipesLiveData.value ?: emptyList()
        val itemsList = mutableListOf<ItemEntry>()
        itemsList.add(ItemEntry("Personalizar item", "custom", ""))
        for (product in products) {
            itemsList.add(ItemEntry(product.name, "products", product.id))
        }
        for (recipe in recipes) {
            itemsList.add(ItemEntry(recipe.name, "recipes", recipe.id))
        }
        val listAdapter =
            ArrayAdapter<ItemEntry>(this, android.R.layout.simple_list_item_1, itemsList)
        itemsListView.adapter = listAdapter
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = itemsList.filter { it.name.contains(s ?: "", ignoreCase = true) }
                listAdapter.clear()
                listAdapter.addAll(filtered)
                listAdapter.notifyDataSetChanged()
            }
        })
        builder.setTitle("Seleccione un ítem")
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
        val dialog = builder.create()
        itemsListView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = listAdapter.getItem(position)
            if (selectedItem != null) {
                if (selectedItem.collection == "custom") {
                    showCustomItemDialog()
                } else {
                    val costValue = when (selectedItem.collection) {
                        "products" -> repository.productsLiveData.value?.find { it.id == selectedItem.collectionId }?.cost
                            ?: 0.0

                        "recipes" -> repository.recipesLiveData.value?.find { it.id == selectedItem.collectionId }?.cost
                            ?: 0.0

                        else -> 0.0
                    }
                    val newItem = MovementItem(
                        selectedItem.collection,
                        selectedItem.collectionId,
                        null,
                        costValue,
                        1.0
                    )
                    movementItems.add(newItem)
                    itemsAdapter.updateItems(movementItems)
                    recalcTotalAmount()
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Validates the required input fields.
     */
    private fun validateInputs(): Boolean {
        if (binding.dateInput.text.toString().trim().isEmpty()) {
            Snackbar.make(binding.root, "La fecha es obligatoria.", Snackbar.LENGTH_LONG).show()
            return false
        }
        if (binding.personSpinner.selectedItem == null) {
            Snackbar.make(binding.root, "Por favor, seleccione una persona.", Snackbar.LENGTH_LONG)
                .show()
            return false
        }
        val totalAmountStr = binding.totalAmountInput.text.toString().trim()
        if (totalAmountStr.isEmpty()) {
            Snackbar.make(binding.root, "El monto total es obligatorio.", Snackbar.LENGTH_LONG)
                .show()
            return false
        }
        if (totalAmountStr.toDoubleOrNull() == null) {
            Snackbar.make(
                binding.root,
                "El monto total debe ser un número válido.",
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }
        if (binding.movementTypeSpinner.selectedItemPosition == 1 && binding.shippingCheckBox.isChecked) {
            val shippingCostStr = binding.shippingCostInput.text.toString().trim()
            if (shippingCostStr.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    "El costo de envío es obligatorio para una venta.",
                    Snackbar.LENGTH_LONG
                ).show()
                return false
            }
            val shippingCost = shippingCostStr.toDoubleOrNull() ?: 0.0
            if (shippingCost <= 0) {
                Snackbar.make(
                    binding.root,
                    "El costo de envío debe ser mayor a 0.",
                    Snackbar.LENGTH_LONG
                ).show()
                return false
            }
            val addressStr = binding.shippingAddressInput.text.toString().trim()
            if (addressStr.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    "La dirección de envío es obligatoria.",
                    Snackbar.LENGTH_LONG
                ).show()
                return false
            }
        }
        return true
    }

    /**
     * Extracts a Movement object from the input fields.
     */
    private fun getMovementFromInputs(): Movement {
        val selectedPersonIndex = binding.personSpinner.selectedItemPosition
        val selectedPerson = personsList.getOrNull(selectedPersonIndex)
        val movementType =
            if (binding.movementTypeSpinner.selectedItemPosition == 1) EMovementType.SALE else EMovementType.PURCHASE
        val totalAmount = binding.totalAmountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
        val shipment = if (movementType == EMovementType.SALE) {
            if (binding.shippingCheckBox.isChecked) {
                val shippingCost =
                    binding.shippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                Shipment(
                    addressId = binding.shippingAddressInput.text.toString().trim(),
                    shippingCost = shippingCost
                )
            } else {
                Shipment(addressId = "", shippingCost = 0.0)
            }
        } else null
        return Movement(
            id = currentMovement?.id ?: "",
            type = movementType,
            personId = selectedPerson?.id ?: "",
            date = selectedDate,
            totalAmount = totalAmount,
            items = movementItems,
            shipment = shipment
        )
    }

    /**
     * Saves the movement (create or update) and cascades product updates to recipes.
     */
    private fun saveMovement() {
        if (!validateInputs()) return
        var movementToSave = getMovementFromInputs()
        if (movementToSave.type == EMovementType.SALE) {
            if (binding.shippingCheckBox.isChecked) {
                val shippingCost =
                    binding.shippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                val rawAddress = binding.shippingAddressInput.text.toString().trim()
                if (shippingCost > 0 && rawAddress.isNotEmpty()) {
                    if (currentMovement != null && currentMovement!!.shipment?.addressId?.isNotEmpty() == true) {
                        val selectedPersonId =
                            personsList.getOrNull(binding.personSpinner.selectedItemPosition)?.id
                                ?: ""
                        AddressesHelper().updateAddress(
                            currentMovement!!.shipment!!.addressId,
                            Address(
                                personId = selectedPersonId,
                                rawAddress = rawAddress,
                                formattedAddress = "",
                                placeId = ""
                            ),
                            onSuccess = {
                                val newShipment = Shipment(
                                    addressId = currentMovement!!.shipment!!.addressId,
                                    shippingCost = shippingCost
                                )
                                movementToSave = movementToSave.copy(shipment = newShipment)
                                saveMovementToFirestore(movementToSave)
                            },
                            onError = { exception ->
                                Snackbar.make(
                                    binding.root,
                                    "Error updating address: ${exception.message}",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        )
                    } else {
                        val selectedPersonId =
                            personsList.getOrNull(binding.personSpinner.selectedItemPosition)?.id
                                ?: ""
                        AddressesHelper().addAddress(
                            Address(
                                personId = selectedPersonId,
                                rawAddress = rawAddress,
                                formattedAddress = "",
                                placeId = ""
                            ),
                            onSuccess = { savedAddress ->
                                val newShipment = Shipment(
                                    addressId = savedAddress.id,
                                    shippingCost = shippingCost
                                )
                                movementToSave = movementToSave.copy(shipment = newShipment)
                                saveMovementToFirestore(movementToSave)
                            },
                            onError = { exception ->
                                Snackbar.make(
                                    binding.root,
                                    "Error saving address: ${exception.message}",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                    return
                } else {
                    movementToSave =
                        movementToSave.copy(shipment = Shipment(addressId = "", shippingCost = 0.0))
                }
            } else {
                movementToSave =
                    movementToSave.copy(shipment = Shipment(addressId = "", shippingCost = 0.0))
            }
        }
        saveMovementToFirestore(movementToSave)
    }

    private fun saveMovementToFirestore(movement: Movement) {
        if (currentMovement == null) {
            MovementsHelper().addMovement(
                movement = movement,
                onSuccess = {
                    movement.items.filter { it.collection == "products" }.forEach { item ->
                        val delta =
                            if (movement.type == EMovementType.PURCHASE) item.quantity else -item.quantity
                        val updateCost = movement.type == EMovementType.PURCHASE
                        ProductsHelper().updateProductStock(
                            productId = item.collectionId,
                            delta = delta,
                            updateCost = updateCost,
                            newCost = item.cost,
                            onSuccess = {
                                RecipesHelper().updateCascadeAffectedRecipesFromProduct(
                                    productId = item.collectionId,
                                    onComplete = {},
                                    onError = { exception ->
                                        Snackbar.make(
                                            binding.root,
                                            "Error en actualización en cascada: ${exception.message}",
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            },
                            onError = { exception ->
                                Snackbar.make(
                                    binding.root,
                                    "Error al actualizar stock: ${exception.message}",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                    Snackbar.make(
                        binding.root,
                        "Movimiento agregado correctamente.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(
                        binding.root,
                        "Error al agregar el movimiento: ${exception.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            )
        } else {
            MovementsHelper().updateMovement(
                movementId = currentMovement!!.id,
                movement = movement,
                onSuccess = {
                    val originalItems = originalProductItems
                    val updatedItems = movement.items.filter { it.collection == "products" }
                    val originalMap = originalItems.groupBy { it.collectionId }
                    val updatedMap = updatedItems.groupBy { it.collectionId }
                    val allProductIds = (originalMap.keys + updatedMap.keys).distinct()
                    allProductIds.forEach { productId ->
                        val originalQty = originalMap[productId]?.sumOf { it.quantity } ?: 0.0
                        val updatedQty = updatedMap[productId]?.sumOf { it.quantity } ?: 0.0
                        val delta =
                            if (movement.type == EMovementType.PURCHASE) updatedQty - originalQty else originalQty - updatedQty
                        if (delta != 0.0) {
                            val updateCost = movement.type == EMovementType.PURCHASE
                            val newCost = updatedMap[productId]?.firstOrNull()?.cost ?: 0.0
                            ProductsHelper().updateProductStock(
                                productId = productId,
                                delta = delta,
                                updateCost = updateCost,
                                newCost = newCost,
                                onSuccess = {
                                    RecipesHelper().updateCascadeAffectedRecipesFromProduct(
                                        productId = productId,
                                        onComplete = {},
                                        onError = { exception ->
                                            Snackbar.make(
                                                binding.root,
                                                "Error en actualización en cascada: ${exception.message}",
                                                Snackbar.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                },
                                onError = { exception ->
                                    Snackbar.make(
                                        binding.root,
                                        "Error al actualizar stock: ${exception.message}",
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                    Snackbar.make(
                        binding.root,
                        "Movimiento actualizado correctamente.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(
                        binding.root,
                        "Error al actualizar el movimiento: ${exception.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}