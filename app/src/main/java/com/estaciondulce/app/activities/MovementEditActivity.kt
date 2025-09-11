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
import com.estaciondulce.app.adapters.DialogAddItemAdapter
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.databinding.ActivityMovementEditBinding
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.models.Address
import com.estaciondulce.app.models.EMovementType
import com.estaciondulce.app.models.EPersonType
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.MovementItem
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.models.Shipment
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
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
    private lateinit var customLoader: CustomLoader
    private var currentMovement: Movement? = null
    private val repository = FirestoreRepository
    private var selectedDate: Date = Date()
    private var selectedDeliveryDate: Date? = null
    private var personsList: List<Person> = listOf()
    private var movementItems: MutableList<MovementItem> = mutableListOf()
    private lateinit var itemsAdapter: MovementItemsAdapter
    private var originalProductItems: List<MovementItem> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        @Suppress("DEPRECATION")
        currentMovement = intent.getParcelableExtra<Movement>("MOVEMENT")
        supportActionBar?.title =
            if (currentMovement != null) "Editar Movimiento" else "Agregar Movimiento"
        binding.dateInput.setOnClickListener { showDatePickerDialog() }
        binding.deliveryDateTimeInput.setOnClickListener { showDateTimePickerDialog() }
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemsAdapter = MovementItemsAdapter(
            movementItems,
            onItemChanged = { recalcTotalAmount() },
            onDeleteClicked = { position ->
                val item = movementItems[position]
                val itemName = when (item.collection) {
                    "products" -> repository.productsLiveData.value?.find { it.id == item.collectionId }?.name ?: "Producto"
                    "recipes" -> repository.recipesLiveData.value?.find { it.id == item.collectionId }?.name ?: "Receta"
                    "custom" -> item.collectionId
                    else -> "Item"
                }
                
                DeleteConfirmationDialog.show(
                    context = this,
                    itemName = itemName,
                    itemType = "item",
                    onConfirm = {
                        movementItems.removeAt(position)
                        itemsAdapter.updateItems(movementItems)
                        recalcTotalAmount()
                    }
                )
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
                val person = persons.find { it.id == movement.personId }
                if (person != null) {
                    binding.personSpinner.setText("${person.name} ${person.lastName}", false)
                }
            }
        }
        
        binding.personSpinner.isEnabled = false
        binding.personSpinnerLayout.isEnabled = false
        setupMovementTypeSpinner()
        currentMovement?.let { movement ->
            selectedDate = movement.movementDate
            binding.dateInput.setText(formatDate(movement.movementDate))
            binding.totalAmountInput.setText(movement.totalAmount.toString())
            val movementTypeText = when (movement.type) {
                EMovementType.PURCHASE -> "Compra"
                EMovementType.SALE -> "Venta"
                else -> "Compra"
            }
            binding.movementTypeSpinner.setText(movementTypeText, false)
            updatePersonSpinnerHint(movementTypeText)
            binding.movementTypeSpinner.isEnabled = false
            binding.movementTypeSpinner.isFocusable = false
            
            binding.itemsCard.visibility = View.VISIBLE
            binding.totalAmountCard.visibility = View.VISIBLE
            binding.saveMovementButton.visibility = View.VISIBLE
            if (movement.type == EMovementType.SALE) {
                binding.shippingRow.visibility = View.VISIBLE
                val shippingCost = movement.shipment?.shippingCost ?: 0.0
                binding.shippingCostInput.setText(shippingCost.toString())
                binding.shippingCheckBox.isChecked = shippingCost != 0.0
                if (binding.shippingCheckBox.isChecked) {
                    binding.deliveryDateTimeContainer.visibility = View.VISIBLE
                    binding.shippingCostContainer.visibility = View.VISIBLE
                    binding.shippingAddressContainer.visibility = View.VISIBLE
                    movement.deliveryDate?.let { deliveryDate ->
                        selectedDeliveryDate = deliveryDate
                        binding.deliveryDateTimeInput.setText(formatDateTime(deliveryDate))
                    }
                    if (movement.shipment?.addressId?.isNotEmpty() == true) {
                        val address =
                            repository.addressesLiveData.value?.find { it.id == movement.shipment.addressId }
                        binding.shippingAddressInput.setText(address?.rawAddress ?: "")
                    }
                } else {
                    binding.deliveryDateTimeContainer.visibility = View.GONE
                    binding.shippingCostContainer.visibility = View.GONE
                    binding.shippingAddressContainer.visibility = View.GONE
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
        binding.movementTypeSpinner.setOnItemClickListener { _, _, position, _ ->
            val movementTypes = listOf("Compra", "Venta")
            val selectedType = movementTypes.getOrNull(position)
            
            binding.itemsCard.visibility = View.VISIBLE
            binding.totalAmountCard.visibility = View.VISIBLE
            binding.saveMovementButton.visibility = View.VISIBLE
            
            val currentPersons = repository.personsLiveData.value ?: emptyList()
            setupPersonSpinner(currentPersons)
            binding.personSpinner.setText("") // Clear current selection
            
            if (selectedType == "Venta") {
                binding.shippingRow.visibility = View.VISIBLE
            } else {
                binding.shippingRow.visibility = View.GONE
                binding.shippingCostInput.setText("0.0")
                binding.shippingAddressInput.setText("")
                binding.shippingCheckBox.isChecked = false
            }
        }
        binding.shippingCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.deliveryDateTimeContainer.visibility = View.VISIBLE
                binding.shippingCostContainer.visibility = View.VISIBLE
                binding.shippingAddressContainer.visibility = View.VISIBLE
            } else {
                binding.deliveryDateTimeContainer.visibility = View.GONE
                binding.shippingCostContainer.visibility = View.GONE
                binding.shippingAddressContainer.visibility = View.GONE
                binding.deliveryDateTimeInput.setText("")
                binding.shippingCostInput.setText("0.0")
                selectedDeliveryDate = null
                recalcTotalAmount()
            }
        }
        binding.shippingCostInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                recalcTotalAmount()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.personSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedPerson = personsList.getOrNull(position)
            if (selectedPerson != null && binding.shippingRow.visibility == View.VISIBLE && binding.shippingCheckBox.isChecked) {
                val address = repository.addressesLiveData.value?.find { it.personId == selectedPerson.id }
                binding.shippingAddressInput.setText(address?.rawAddress ?: "")
            }
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
     * Displays a modern dialog to add a custom item.
     */
    private fun showCustomItemDialog() {
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_custom_item, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val customItemNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.customItemNameInput)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.cancelButton)
        val addButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.addButton)

        customItemNameInput.requestFocus()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        addButton.setOnClickListener {
            val customName = customItemNameInput.text.toString().trim()
            if (customName.isNotEmpty()) {
                val itemExists = movementItems.any { 
                    it.collection == "custom" && it.customName == customName 
                }
                
                if (itemExists) {
                    CustomToast.showError(this, "Este ítem ya fue agregado al movimiento.")
                } else {
                    val newItem = MovementItem("custom", "", customName, 1.0, 1.0)
                    movementItems.add(newItem)
                    itemsAdapter.updateItems(movementItems)
                    recalcTotalAmount()
                }
                dialog.dismiss()
            } else {
                CustomToast.showError(this, "El nombre no puede estar vacío.")
            }
        }

        dialog.show()
    }

    /**
     * Sets up the person spinner with the provided list, filtered by movement type.
     */
    private fun setupPersonSpinner(persons: List<Person>) {
        val movementType = binding.movementTypeSpinner.text.toString()
        val filteredPersons = when (movementType) {
            "Compra" -> persons.filter { it.type == EPersonType.PROVIDER.dbValue }
            "Venta" -> persons.filter { it.type == EPersonType.CLIENT.dbValue }
            else -> emptyList() // No persons if no type selected
        }
        
        val personNames = filteredPersons.map { "${it.name} ${it.lastName}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, personNames)
        binding.personSpinner.setAdapter(adapter)
        
        updatePersonSpinnerHint(movementType)
        binding.personSpinner.isEnabled = movementType.isNotEmpty()
        binding.personSpinnerLayout.isEnabled = movementType.isNotEmpty()
    }
    
    /**
     * Updates the person spinner hint based on movement type.
     */
    private fun updatePersonSpinnerHint(movementType: String) {
        val hint = when (movementType) {
            "Compra" -> EPersonType.PROVIDER.displayValue
            "Venta" -> EPersonType.CLIENT.displayValue
            else -> "Persona"
        }
        binding.personSpinnerLayout.hint = hint
    }

    /**
     * Sets up the movement type spinner with "Compra" and "Venta" options.
     */
    private fun setupMovementTypeSpinner() {
        val movementTypes = listOf("Compra", "Venta")
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, movementTypes)
        binding.movementTypeSpinner.setAdapter(adapter)
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

    private fun showDateTimePickerDialog() {
        val calendar = Calendar.getInstance()
        selectedDeliveryDate?.let { calendar.time = it }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
                selectedDeliveryDate = calendar.time
                binding.deliveryDateTimeInput.setText(formatDateTime(selectedDeliveryDate!!))
            }, hour, minute, true).show()
        }, year, month, day).show()
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date)
    }

    private fun formatDateTime(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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
        binding.itemsHeader.visibility = View.VISIBLE // Siempre visible
        binding.itemsHeadersContainer.visibility = if (movementItems.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Displays a dialog for adding an item by searching products and recipes.
     */
    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_item, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<android.widget.EditText>(com.estaciondulce.app.R.id.searchEditText)
        val itemsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.estaciondulce.app.R.id.itemsRecyclerView)
        val emptyState = dialogView.findViewById<android.widget.LinearLayout>(com.estaciondulce.app.R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)

        itemsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val products = repository.productsLiveData.value ?: emptyList()
        val recipes = repository.recipesLiveData.value ?: emptyList()
        
        val currentMovementType = currentMovement?.type
        val isPurchase = when {
            currentMovementType != null -> currentMovementType == EMovementType.PURCHASE
            binding.movementTypeSpinner.text.toString().contains("Compra") -> true
            binding.movementTypeSpinner.text.toString().contains("Venta") -> false
            else -> true // Default to purchase
        }
        
        val allItems = mutableListOf<ItemEntry>()
        
        if (isPurchase) {
            val sortedProducts = products.sortedBy { it.name }
            for (product in sortedProducts) {
                allItems.add(ItemEntry(product.name, "products", product.id))
            }
        } else {
            allItems.add(ItemEntry("Personalizar item", "custom", ""))
            val sortedRecipes = recipes.sortedBy { it.name }
            for (recipe in sortedRecipes) {
                allItems.add(ItemEntry(recipe.name, "recipes", recipe.id))
            }
        }

        val dialogAdapter = DialogAddItemAdapter(allItems, products, recipes, isPurchase) { selectedItem ->
            if (selectedItem.collection == "custom") {
                dialog.dismiss()
                showCustomItemDialog()
            } else {
                val itemExists = movementItems.any { 
                    it.collection == selectedItem.collection && it.collectionId == selectedItem.collectionId 
                }
                
                if (itemExists) {
                    CustomToast.showError(this, "Este ítem ya fue agregado al movimiento.")
                } else {
                    val costValue = when (selectedItem.collection) {
                        "products" -> {
                            repository.productsLiveData.value?.find { it.id == selectedItem.collectionId }?.cost ?: 0.0
                        }
                        "recipes" -> {
                            val recipe = repository.recipesLiveData.value?.find { it.id == selectedItem.collectionId }
                            if (isPurchase) recipe?.cost ?: 0.0 else recipe?.salePrice ?: 0.0
                        }
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
                dialog.dismiss()
            }
        }
        
        itemsRecyclerView.adapter = dialogAdapter

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                
                if (query.length < 3) {
                    itemsRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.GONE
                    dialogAdapter.updateItems(emptyList())
                } else {
                    val filtered = allItems.filter { 
                        it.name.contains(query, ignoreCase = true) 
                    }.sortedBy { it.name }
                    
                    if (filtered.isEmpty()) {
                        itemsRecyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        itemsRecyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        dialogAdapter.updateItems(filtered)
                    }
                }
            }
        })

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Validates the required input fields.
     */
    private fun validateInputs(): Boolean {
        if (binding.dateInput.text.toString().trim().isEmpty()) {
            CustomToast.showError(this, "La fecha es obligatoria.")
            return false
        }
        if (binding.personSpinner.text.toString().trim().isEmpty()) {
            CustomToast.showError(this, "Por favor, seleccione una persona.")
            return false
        }
        val totalAmountStr = binding.totalAmountInput.text.toString().trim()
        if (totalAmountStr.isEmpty()) {
            CustomToast.showError(this, "El monto total es obligatorio.")
            return false
        }
        if (totalAmountStr.toDoubleOrNull() == null) {
            CustomToast.showError(this, "El monto total debe ser un número válido.")
            return false
        }
        val movementTypeText = binding.movementTypeSpinner.text.toString()
        if (movementTypeText == "Venta" && binding.shippingCheckBox.isChecked) {
            val shippingCostStr = binding.shippingCostInput.text.toString().trim()
            if (shippingCostStr.isEmpty()) {
                CustomToast.showError(this, "El costo de envío es obligatorio para una venta.")
                return false
            }
            val shippingCost = shippingCostStr.toDoubleOrNull() ?: 0.0
            if (shippingCost <= 0) {
                CustomToast.showError(this, "El costo de envío debe ser mayor a 0.")
                return false
            }
            val addressStr = binding.shippingAddressInput.text.toString().trim()
            if (addressStr.isEmpty()) {
                CustomToast.showError(this, "La dirección de envío es obligatoria.")
                return false
            }
        }
        return true
    }

    /**
     * Extracts a Movement object from the input fields.
     */
    private fun getMovementFromInputs(): Movement {
        val selectedPersonName = binding.personSpinner.text.toString()
        val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
        val movementTypeText = binding.movementTypeSpinner.text.toString()
        val movementType = if (movementTypeText == "Venta") EMovementType.SALE else EMovementType.PURCHASE
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
            movementDate = selectedDate,
            totalAmount = totalAmount,
            items = movementItems,
            shipment = shipment,
            deliveryDate = selectedDeliveryDate
        )
    }

    /**
     * Saves the movement (create or update) and cascades product updates to recipes.
     */
    private fun saveMovement() {
        if (!validateInputs()) return
        
        customLoader.show()
        
        var movementToSave = getMovementFromInputs()
        if (movementToSave.type == EMovementType.SALE) {
            if (binding.shippingCheckBox.isChecked) {
                val shippingCost =
                    binding.shippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                val rawAddress = binding.shippingAddressInput.text.toString().trim()
                if (shippingCost > 0 && rawAddress.isNotEmpty()) {
                    if (currentMovement != null && currentMovement!!.shipment?.addressId?.isNotEmpty() == true) {
                        val selectedPersonName = binding.personSpinner.text.toString()
                        val selectedPersonId = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }?.id ?: ""
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
                                CustomToast.showError(this, "Error updating address: ${exception.message}")
                            }
                        )
                    } else {
                        val selectedPersonName = binding.personSpinner.text.toString()
                        val selectedPersonId = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }?.id ?: ""
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
                                CustomToast.showError(this, "Error saving address: ${exception.message}")
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
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Movimiento agregado correctamente.")
                    finish()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al agregar el movimiento: ${exception.message}")
                }
            )
        } else {
            val originalMovementId = currentMovement!!.id
            
            MovementsHelper().addMovement(
                movement = movement,
                onSuccess = {
                    MovementsHelper().deleteMovement(
                        movementId = originalMovementId,
                        onSuccess = {
                            customLoader.hide()
                            CustomToast.showSuccess(this, "Movimiento actualizado correctamente.")
                            finish()
                        },
                        onError = { _ ->
                            customLoader.hide()
                            CustomToast.showError(this, "Movimiento actualizado, pero hubo un error al eliminar el original. Contacte soporte.")
                            finish()
                        }
                    )
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al crear el movimiento actualizado: ${exception.message}")
                }
            )
        }
    }
}