package com.estaciondulce.app.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.adapters.MovementItemsAdapter
import com.estaciondulce.app.adapters.DialogAddItemAdapter
import com.estaciondulce.app.adapters.PersonSearchAdapter
import com.estaciondulce.app.adapters.AddressSelectionAdapter
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.databinding.ActivityMovementEditBinding
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.helpers.ShipmentSettingsHelper
import com.estaciondulce.app.helpers.DistanceMatrixHelper
import com.estaciondulce.app.models.Address
import com.estaciondulce.app.models.EMovementType
import com.estaciondulce.app.models.EPersonType
import com.estaciondulce.app.models.EShipmentStatus
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.MovementItem
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.models.Shipment
import com.estaciondulce.app.models.ShipmentSettings
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
    private var discountAmount: Double = 0.0
    private var itemsSubtotal: Double = 0.0 // Suma de todos los ítems (cantidad * precio)
    private var selectedAddress: Address? = null
    private val addressesHelper = AddressesHelper()
    private val shipmentSettingsHelper = ShipmentSettingsHelper()
    private val distanceMatrixHelper = DistanceMatrixHelper()
    private var calculatedShippingCost: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        @Suppress("DEPRECATION")
        currentMovement = intent.getParcelableExtra<Movement>("MOVEMENT")
        val personId = intent.getStringExtra("PERSON_ID")
        supportActionBar?.title =
            if (currentMovement != null) "Editar Movimiento" else "Agregar Movimiento"
        binding.dateInput.setOnClickListener { showMovementDateTimePickerDialog() }
        binding.deliveryDateTimeInput.setOnClickListener { showDateTimePickerDialog() }
        binding.discountInput.addTextChangedListener(createDiscountWatcher())
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemsAdapter = MovementItemsAdapter(
            movementItems,
            onItemChanged = { recalcTotalAmount() },
            onDeleteClicked = { position ->
                val item = movementItems[position]
                val itemName = when (item.collection) {
                    "products" -> repository.productsLiveData.value?.find { it.id == item.collectionId }?.name ?: "Item"
                    "recipes" -> repository.recipesLiveData.value?.find { it.id == item.collectionId }?.name ?: "Item"
                    "custom" -> item.collectionId
                    else -> "Item"
                }
                
                DeleteConfirmationDialog.show(
                    context = this,
                    itemName = itemName,
                    itemType = "item",
                    onConfirm = {
                        movementItems.removeAt(position)
                        itemsAdapter.notifyItemRemoved(position)
                        itemsAdapter.notifyItemRangeChanged(position, movementItems.size - position)
                        recalcTotalAmount() // Need to recalculate after deletion
                    }
                )
            },
            getDisplayName = { collection, collectionId ->
                when (collection) {
                    "products" -> repository.productsLiveData.value?.find { it.id == collectionId }?.name
                        ?: "Desconocido"

                    "recipes" -> repository.recipesLiveData.value?.find { it.id == collectionId }?.name
                        ?: "Desconocido"

                    "custom" -> {
                        val customItem = movementItems.find { it.collection == "custom" && it.collectionId == collectionId }
                        when (customItem?.collectionId) {
                            "discount" -> "Descuento"
                            else -> customItem?.customName ?: "Personalizado"
                        }
                    }

                    else -> "Desconocido"
                }
            }
        )
        binding.itemsRecyclerView.adapter = itemsAdapter
        binding.addItemButton.setOnClickListener { showAddItemDialog() }
        repository.personsLiveData.observe(this) { persons ->
            personsList = persons
            currentMovement?.let { movement ->
                val person = persons.find { it.id == movement.personId }
                if (person != null) {
                    binding.personSpinner.setText("${person.name} ${person.lastName}")
                }
            } ?: run {
                personId?.let { id ->
                    val person = persons.find { it.id == id }
                    if (person != null) {
                        binding.personSpinner.setText("${person.name} ${person.lastName}")
                        
                        when (person.type) {
                            EPersonType.PROVIDER.dbValue -> {
                                binding.movementTypeSpinner.setText("Compra", false)
                                updatePersonSpinnerHint("Compra")
                                binding.shippingRow.visibility = View.GONE
                                binding.discountCard.visibility = View.GONE
                            }
                            EPersonType.CLIENT.dbValue -> {
                                binding.movementTypeSpinner.setText("Venta", false)
                                updatePersonSpinnerHint("Venta")
                                binding.shippingRow.visibility = View.VISIBLE
                                binding.discountCard.visibility = View.VISIBLE
                            }
                        }
                        
                        binding.itemsCard.visibility = View.VISIBLE
                        binding.totalAmountCard.visibility = View.VISIBLE
                        binding.saveMovementButton.visibility = View.VISIBLE
                        
                        binding.movementTypeSpinner.isEnabled = false
                    }
                }
            }
        }
        
        binding.personSpinner.setOnClickListener { showPersonSearchDialog() }
        setupMovementTypeSpinner()
        
        if (currentMovement == null) {
            binding.movementTypeSpinner.setOnItemClickListener { _, _, position, _ ->
                val movementTypes = listOf("Compra", "Venta")
                val selectedType = movementTypes.getOrNull(position)
                
                binding.itemsCard.visibility = View.VISIBLE
                binding.totalAmountCard.visibility = View.VISIBLE
                binding.saveMovementButton.visibility = View.VISIBLE
                
                binding.personSpinner.setText("") // Clear current selection
                updatePersonSpinnerHint(selectedType ?: "")
                
                if (selectedType == "Venta") {
                    binding.shippingRow.visibility = View.VISIBLE
                    binding.discountCard.visibility = View.VISIBLE
                    binding.detailCard.visibility = View.VISIBLE
                    binding.discountInput.setText("0")
                    discountAmount = 0.0
                    recalcTotalAmount()
                } else {
                    binding.shippingRow.visibility = View.GONE
                    binding.discountCard.visibility = View.GONE
                    binding.detailCard.visibility = View.GONE
                    binding.shippingAddressInput.setText("")
                    binding.shippingCheckBox.isChecked = false
                    recalcTotalAmount()
                }
            }
        }
        
        currentMovement?.let { movement ->
            selectedDate = movement.movementDate
            binding.dateInput.setText(formatDateTime(movement.movementDate))
            binding.totalAmountInput.setText(movement.totalAmount.toString())
            val movementTypeText = when (movement.type) {
                EMovementType.PURCHASE -> "Compra"
                EMovementType.SALE -> "Venta"
                else -> "Compra"
            }
            binding.movementTypeSpinner.setText(movementTypeText, false)
            binding.movementTypeSpinner.isEnabled = false
            binding.movementTypeSpinner.isFocusable = false
            binding.movementTypeSpinner.isClickable = false
            updatePersonSpinnerHint(movementTypeText)
            
            binding.itemsCard.visibility = View.VISIBLE
            binding.totalAmountCard.visibility = View.VISIBLE
            binding.saveMovementButton.visibility = View.VISIBLE
            if (movement.type == EMovementType.SALE) {
                binding.shippingRow.visibility = View.VISIBLE
                binding.discountCard.visibility = View.VISIBLE
                binding.detailCard.visibility = View.VISIBLE
                
                val hasShipment = movement.shipment != null
                binding.shippingCheckBox.isChecked = hasShipment
                
                if (hasShipment) {
                    val shippingCost = movement.shipment?.cost ?: 0.0
                    val calculatedCost = movement.shipment?.calculatedCost ?: 0.0
                    
                    // Load shipping cost into the input
                    binding.finalShippingCostInput.setText(String.format("%.2f", shippingCost))
                    calculatedShippingCost = calculatedCost
                    
                    binding.shippingAddressContainer.visibility = View.VISIBLE
                    binding.shippingDetailsContainer.visibility = View.VISIBLE
                    
                    movement.shipment?.date?.let { shipmentDate ->
                        selectedDeliveryDate = shipmentDate
                        binding.deliveryDateTimeInput.setText(formatDateTime(shipmentDate))
                    }
                    
                    if (movement.shipment?.addressId?.isNotEmpty() == true) {
                        // Load address from person's addresses
                        val movementPersonId = movement.personId
                        addressesHelper.getAddressesForPerson(
                            personId = movementPersonId,
                            onSuccess = { addresses ->
                                val shipment = movement.shipment
                                val address = addresses.find { it.id == shipment.addressId }
                                if (address != null) {
                                    selectedAddress = address
                                    binding.shippingAddressInput.setText("${address.label}: ${address.formattedAddress}")
                                } else {
                                    // Fallback to formatted address from shipment
                                    val fallbackText = shipment.formattedAddress
                                    binding.shippingAddressInput.setText(fallbackText)
                                    CustomToast.showWarning(this@MovementEditActivity, "Dirección original no encontrada en la lista actual")
                                }
                            },
                            onError = { _ ->
                                // Fallback to formatted address from shipment
                                val shipment = movement.shipment
                                val fallbackText = shipment.formattedAddress
                                binding.shippingAddressInput.setText(fallbackText)
                                CustomToast.showError(this@MovementEditActivity, "Error al cargar direcciones guardadas")
                            }
                        )
                    }
                } else {
                    clearShipmentData()
                }
                
                val discountItem = movement.items.find { it.collection == "custom" && it.collectionId == "discount" }
                if (discountItem != null) {
                    binding.discountInput.setText((-discountItem.cost).toString())
                    discountAmount = -discountItem.cost
                } else {
                    binding.discountInput.setText("0")
                    discountAmount = 0.0
                }
                
                // Load detail if exists
                binding.detailInput.setText(movement.detail)
            } else {
                binding.shippingRow.visibility = View.GONE
                binding.discountCard.visibility = View.GONE
                binding.detailCard.visibility = View.GONE
            }
            movementItems.clear()
            val regularItems = movement.items.filter { it.collection != "custom" || it.collectionId != "discount" }
            
            movementItems.addAll(regularItems)
            itemsAdapter.updateItems(movementItems.toMutableList())
            
            recalcTotalAmount()
            originalProductItems =
                movement.items.filter { it.collection == "products" }.map { it.copy() }
        } ?: run {
            binding.dateInput.setText(formatDateTime(selectedDate))
        }
        binding.shippingCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.shippingAddressContainer.visibility = View.VISIBLE
                binding.shippingDetailsContainer.visibility = View.VISIBLE
            } else {
                if (hasExistingShipmentData()) {
                    showShipmentRemovalConfirmation()
                } else {
                    clearShipmentData()
                }
            }
        }
        
        // Configure shipping cost calculation
        
        // Initialize shipment settings
        shipmentSettingsHelper.startListening()
        shipmentSettingsHelper.shipmentSettings.observe(this) { settings ->
            android.util.Log.d("MovementEdit", "Shipment settings updated: $settings")
        }
        binding.finalShippingCostInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                recalcTotalAmount()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.shippingAddressInput.setOnClickListener { showAddressSelectionDialog() }
        binding.saveMovementButton.setOnClickListener { saveMovement() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            android.util.Log.d("MovementEdit", "onActivityResult - Intent extras: ${data?.extras?.keySet()}")
            @Suppress("DEPRECATION")
            val address = data?.getParcelableExtra<Address>("result_address")
            android.util.Log.d("MovementEdit", "onActivityResult - AddressPicker returned: ${address?.id}, label: ${address?.label}")
            
            if (address != null) {
                // Get the selected person to save the address
                val selectedPersonName = binding.personSpinner.text.toString()
                val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
                
                if (selectedPerson != null) {
                    android.util.Log.d("MovementEdit", "Saving new address to Firestore for person: ${selectedPerson.id}")
                    
                    // Save the address to Firestore
                    addressesHelper.addAddressToPerson(
                        personId = selectedPerson.id,
                        address = address,
                        onSuccess = { savedAddress ->
                            android.util.Log.d("MovementEdit", "Address saved successfully: ${savedAddress.id}")
                            selectedAddress = savedAddress
                            val displayText = "${savedAddress.label}: ${savedAddress.formattedAddress}"
                            binding.shippingAddressInput.setText(displayText)
                            customLoader.hide()
                            CustomToast.showSuccess(this, "Nueva dirección agregada y seleccionada: ${savedAddress.label}")
                        },
                        onError = { exception ->
                            android.util.Log.e("MovementEdit", "Error saving address: ${exception.message}", exception)
                            customLoader.hide()
                            CustomToast.showError(this, "Error al guardar la dirección: ${exception.message}")
                        }
                    )
                } else {
                    android.util.Log.e("MovementEdit", "Selected person not found when trying to save address")
                    customLoader.hide()
                    CustomToast.showError(this, "Error: No se pudo encontrar la persona seleccionada.")
                }
            } else {
                android.util.Log.e("MovementEdit", "Address is null in onActivityResult")
                customLoader.hide()
                CustomToast.showError(this, "Error al obtener la nueva dirección.")
            }
        } else if (requestCode == 2001 && resultCode == RESULT_CANCELED) {
            android.util.Log.d("MovementEdit", "AddressPicker canceled")
            customLoader.hide()
            CustomToast.showWarning(this, "Creación de dirección cancelada.")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish(); true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listeners
        shipmentSettingsHelper.stopListening()
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
                    val newItem = MovementItem("custom", "", customName, 0.01, 1.0)
                    movementItems.add(newItem)
                    itemsAdapter.notifyItemInserted(movementItems.size - 1)
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
     * Creates a TextWatcher for the discount input.
     */
    private fun createDiscountWatcher(): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val discount = s.toString().toDoubleOrNull() ?: 0.0
                val validDiscount = if (discount < 0) 0.0 else discount
                if (discount != validDiscount) {
                    binding.discountInput.setText(validDiscount.toString())
                }
                discountAmount = validDiscount
                recalcTotalAmount()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
    }


    /**
     * Shows the address selection dialog.
     */
    private fun showAddressSelectionDialog() {
        android.util.Log.d("MovementEdit", "showAddressSelectionDialog() called")
        
        val selectedPersonName = binding.personSpinner.text.toString()
        android.util.Log.d("MovementEdit", "Selected person name: '$selectedPersonName'")
        
        if (selectedPersonName.isEmpty()) {
            android.util.Log.w("MovementEdit", "No person selected")
            CustomToast.showError(this, "Por favor, seleccione primero una persona.")
            return
        }

        val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
        android.util.Log.d("MovementEdit", "Found person: ${selectedPerson?.id}, name: ${selectedPerson?.name} ${selectedPerson?.lastName}")
        
        if (selectedPerson == null) {
            android.util.Log.e("MovementEdit", "Person not found in personsList")
            CustomToast.showError(this, "No se pudo encontrar la persona seleccionada.")
            return
        }

        android.util.Log.d("MovementEdit", "Creating dialog for person: ${selectedPerson.id}")
        
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_address_selection, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.searchEditText)
        val addressesRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.estaciondulce.app.R.id.addressesRecyclerView)
        val emptyState = dialogView.findViewById<android.widget.LinearLayout>(com.estaciondulce.app.R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)
        android.util.Log.d("MovementEdit", "Dialog views found - searchEditText: ${searchEditText != null}, addressesRecyclerView: ${addressesRecyclerView != null}, emptyState: ${emptyState != null}, closeButton: ${closeButton != null}")

        addressesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // Load addresses for the selected person
        android.util.Log.d("MovementEdit", "Loading addresses for person: ${selectedPerson.id}")
        addressesHelper.getAddressesForPerson(
            personId = selectedPerson.id,
            onSuccess = { addresses ->
                android.util.Log.d("MovementEdit", "Addresses loaded successfully: ${addresses.size} addresses found")
                addresses.forEach { address ->
                    android.util.Log.d("MovementEdit", "Address: id=${address.id}, label=${address.label}, formattedAddress=${address.formattedAddress}")
                }
                
                val sortedAddresses = addresses.sortedBy { it.label }
                android.util.Log.d("MovementEdit", "Addresses sorted, creating adapter with ${sortedAddresses.size} addresses")
                
                val dialogAdapter = AddressSelectionAdapter(sortedAddresses) { selectedAddress ->
                    android.util.Log.d("MovementEdit", "AddressSelectionAdapter callback triggered for address: ${selectedAddress.id}")
                    this.selectedAddress = selectedAddress
                    val displayText = "${selectedAddress.label}: ${selectedAddress.formattedAddress}"
                    binding.shippingAddressInput.setText(displayText)
                    
                    // Calculate shipping cost automatically when address is selected
                    calculateShippingCostForAddress(selectedAddress)
                    
                    android.util.Log.d("MovementEdit", "Address selected and set in input: id=${selectedAddress.id}, label=${selectedAddress.label}, formattedAddress=${selectedAddress.formattedAddress}")
                    android.util.Log.d("MovementEdit", "About to dismiss dialog")
                    dialog.dismiss()
                    android.util.Log.d("MovementEdit", "Dialog dismissed")
                }
                
                addressesRecyclerView.adapter = dialogAdapter
                android.util.Log.d("MovementEdit", "Adapter set on RecyclerView")

                if (sortedAddresses.isEmpty()) {
                    android.util.Log.d("MovementEdit", "No addresses found, showing empty state")
                    addressesRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    android.util.Log.d("MovementEdit", "Addresses found, showing RecyclerView")
                    addressesRecyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }

                // Search functionality
                searchEditText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {}
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s?.toString() ?: ""
                        
                        if (query.isEmpty()) {
                            dialogAdapter.updateAddresses(sortedAddresses)
                        } else {
                            val filtered = sortedAddresses.filter { 
                                it.label.contains(query, ignoreCase = true) || 
                                it.formattedAddress.contains(query, ignoreCase = true)
                            }
                            dialogAdapter.updateAddresses(filtered)
                        }
                    }
                })
            },
            onError = { exception ->
                android.util.Log.e("MovementEdit", "Error loading addresses: ${exception.message}", exception)
                CustomToast.showError(this, "Error al cargar direcciones: ${exception.message}")
                addressesRecyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
        )


        closeButton.setOnClickListener {
            android.util.Log.d("MovementEdit", "Close button clicked")
            dialog.dismiss()
        }

        android.util.Log.d("MovementEdit", "About to show dialog")
        dialog.show()
        android.util.Log.d("MovementEdit", "Dialog shown")
    }


    /**
     * Shows the person search dialog.
     */
    private fun showPersonSearchDialog() {
        val movementType = binding.movementTypeSpinner.text.toString()
        if (movementType.isEmpty()) {
            CustomToast.showError(this, "Por favor, seleccione primero el tipo de movimiento.")
            return
        }

                val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_person_search, null)
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create()

                val searchEditText = dialogView.findViewById<android.widget.EditText>(com.estaciondulce.app.R.id.searchEditText)
                val personsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.estaciondulce.app.R.id.personsRecyclerView)
                val emptyState = dialogView.findViewById<android.widget.LinearLayout>(com.estaciondulce.app.R.id.emptyState)
                val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)
                val dialogTitle = dialogView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.dialogTitle)
                
                val titleText = when (movementType) {
                    "Compra" -> "Seleccionar proveedor"
                    "Venta" -> "Seleccionar cliente"
                    else -> "Seleccionar persona"
                }
                dialogTitle.text = titleText

        personsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val filteredPersons = when (movementType) {
            "Compra" -> personsList.filter { it.type == EPersonType.PROVIDER.dbValue }
            "Venta" -> personsList.filter { it.type == EPersonType.CLIENT.dbValue }
            else -> emptyList()
        }

        val dialogAdapter = PersonSearchAdapter(filteredPersons) { selectedPerson ->
            binding.personSpinner.setText("${selectedPerson.name} ${selectedPerson.lastName}")
            dialog.dismiss()
        }
        
        personsRecyclerView.adapter = dialogAdapter

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                
                if (query.length < 3) {
                    personsRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.GONE
                    dialogAdapter.updatePersons(emptyList())
                } else {
                    val filtered = filteredPersons.filter { 
                        "${it.name} ${it.lastName}".contains(query, ignoreCase = true) 
                    }.sortedBy { "${it.name} ${it.lastName}" }
                    
                    if (filtered.isEmpty()) {
                        personsRecyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        personsRecyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        dialogAdapter.updatePersons(filtered)
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
     * Sets up the movement type spinner with "Compra" and "Venta" options.
     */
    private fun setupMovementTypeSpinner() {
        val movementTypes = listOf("Compra", "Venta")
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, movementTypes)
        binding.movementTypeSpinner.setAdapter(adapter)
    }
    
    /**
     * Updates the person spinner hint based on movement type.
     */
    private fun updatePersonSpinnerHint(movementType: String) {
        val hint = when (movementType) {
            "Compra" -> "Seleccionar proveedor"
            "Venta" -> "Seleccionar cliente"
            else -> "Seleccionar persona"
        }
        binding.personSpinnerLayout.hint = hint
    }

    /**
     * Displays a DateTimePickerDialog to select date and time for movement.
     */
    private fun showMovementDateTimePickerDialog() {
        val calendar = Calendar.getInstance().apply { time = selectedDate }
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                calendar.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
                selectedDate = calendar.time
                binding.dateInput.setText(formatDateTime(selectedDate))
            }, hour, minute, true).show()
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
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("es"))
        val formatted = sdf.format(date)
        return formatted.replace("sept.", "sep")
    }

    /**
     * Checks if there is existing shipment data that would require confirmation to remove.
     */
    private fun hasExistingShipmentData(): Boolean {
        val cost = binding.finalShippingCostInput.text.toString().toDoubleOrNull() ?: 0.0
        val address = binding.shippingAddressInput.text.toString().trim()
        val hasDate = selectedDeliveryDate != null
        
        return cost > 0.0 || address.isNotEmpty() || hasDate
    }

    /**
     * Shows confirmation dialog when removing shipment with existing data.
     */
    private fun showShipmentRemovalConfirmation() {
        val selectedPersonName = binding.personSpinner.text.toString()
        val personName = if (selectedPersonName.isNotEmpty()) {
            selectedPersonName
        } else {
            "esta persona"
        }
        
        DeleteConfirmationDialog.show(
            context = this,
            itemName = "el envío a '$personName'",
            itemType = "envío",
            onConfirm = {
                clearShipmentData()
            },
            onCancel = {
                binding.shippingCheckBox.isChecked = true
            }
        )
    }

    /**
     * Recalculates shipping cost using Distance Matrix API.
     */
    private fun calculateShippingCostForAddress(address: Address) {
        if (address.latitude == null || address.longitude == null) {
            CustomToast.showError(this, "La dirección seleccionada no tiene coordenadas válidas")
            return
        }
        
        val settings = shipmentSettingsHelper.getCurrentSettings()
        if (settings == null || !shipmentSettingsHelper.areSettingsValid()) {
            CustomToast.showError(this, "Configuración de envío no disponible. Contacte al administrador.")
            return
        }
        
        val destination = "${address.latitude},${address.longitude}"
        
        customLoader.show()
        
        distanceMatrixHelper.calculateDistance(settings.baseAddress, destination) { distance, error ->
            runOnUiThread {
                customLoader.hide()
                
                if (error != null) {
                    CustomToast.showError(this, "Error al calcular distancia: $error")
                    return@runOnUiThread
                }
                
                if (distance == null || distance <= 0) {
                    CustomToast.showError(this, "No se pudo calcular la distancia")
                    return@runOnUiThread
                }
                
                // Calculate shipping cost using new formula
                calculatedShippingCost = distanceMatrixHelper.calculateShippingCost(
                    distance, settings.fuelPrice, settings.litersPerKm
                )
                
                // Update UI with 2 decimal places
                val formattedCost = String.format("%.2f", calculatedShippingCost)
                binding.finalShippingCostInput.setText(formattedCost)
                
                // Show toast with distance and cost
                val formattedDistance = String.format("%.2f", distance)
                CustomToast.showSuccess(this, "Distancia: ${formattedDistance} km - Costo: $${formattedCost}")
                recalcTotalAmount()
            }
        }
    }

    /**
     * Clears all shipment data and hides related UI elements.
     */
    private fun clearShipmentData() {
        binding.shippingAddressContainer.visibility = View.GONE
        binding.shippingDetailsContainer.visibility = View.GONE
        binding.deliveryDateTimeInput.setText("")
        binding.shippingAddressInput.setText("")
        binding.shippingAddressInput.hint = "Seleccionar dirección"
        binding.finalShippingCostInput.setText("0.00")
        selectedDeliveryDate = null
        selectedAddress = null
        calculatedShippingCost = 0.0
        recalcTotalAmount()
    }

    /**
     * Recalculates the total amount based on item cost, quantity, discount and shipping cost if shipping is enabled.
     */
    private fun recalcTotalAmount() {
        itemsSubtotal = movementItems.sumOf { it.cost * it.quantity }
        
        binding.subtotalAmountText.text = String.format("$%.2f", itemsSubtotal)
        binding.subtotalSection.visibility = if (movementItems.isNotEmpty()) View.VISIBLE else View.GONE
        
        val shippingCost =
            if (binding.shippingRow.visibility == View.VISIBLE && binding.shippingCheckBox.isChecked) {
                binding.finalShippingCostInput.text.toString().toDoubleOrNull() ?: 0.0
            } else 0.0
        
        val discount = if (binding.discountCard.visibility == View.VISIBLE) {
            binding.discountInput.text.toString().toDoubleOrNull() ?: 0.0
        } else 0.0
        
        val total = itemsSubtotal - discount + shippingCost
        binding.totalAmountInput.setText(String.format("%.2f", total))
        
        binding.itemsHeader.visibility = View.VISIBLE
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
        
        val allItems = buildSearchResults(products, recipes, isPurchase)

        val dialogAdapter = DialogAddItemAdapter(allItems) { selectedItem ->
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
                    val costValue = when (selectedItem.type) {
                        com.estaciondulce.app.models.ItemType.PRODUCT -> {
                            if (isPurchase) {
                                val cost = repository.productsLiveData.value?.find { it.id == selectedItem.id }?.cost ?: 0.0
                                if (cost <= 0.0) 0.01 else cost
                            } else {
                                selectedItem.price
                            }
                        }
                        com.estaciondulce.app.models.ItemType.RECIPE -> {
                            val recipe = repository.recipesLiveData.value?.find { it.id == selectedItem.id }
                            val cost = if (isPurchase) recipe?.cost ?: 0.0 else recipe?.salePrice ?: 0.0
                            if (cost <= 0.0) 0.01 else cost
                        }
                    }
                    val newItem = MovementItem(
                        selectedItem.collection,
                        selectedItem.collectionId,
                        null,
                        costValue,
                        1.0
                    )
                    movementItems.add(newItem)
                    itemsAdapter.notifyItemInserted(movementItems.size - 1)
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
                    emptyState.visibility = View.VISIBLE
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
        
        if (movementItems.isEmpty()) {
            CustomToast.showError(this, "Debe agregar al menos un ítem al movimiento.")
            return false
        }
        
        for (item in movementItems) {
            if (item.quantity <= 0) {
                CustomToast.showError(this, "Las cantidades de los ítems deben ser mayores a 0.")
                return false
            }
            if (item.cost <= 0) {
                CustomToast.showError(this, "Los precios de los ítems deben ser mayores a 0.")
                return false
            }
        }
        
        val movementTypeText = binding.movementTypeSpinner.text.toString()
        if (movementTypeText == "Venta") {
            val discountStr = binding.discountInput.text.toString().trim()
            val discount = discountStr.toDoubleOrNull() ?: 0.0
            if (discount < 0) {
                CustomToast.showError(this, "El descuento no puede ser negativo.")
                return false
            }
            if (discount > itemsSubtotal) {
                CustomToast.showError(this, "El descuento no puede ser mayor al subtotal.")
                return false
            }
            
            // Validate detail field
            val detail = binding.detailInput.text.toString().trim()
            if (detail.length > 128) {
                CustomToast.showError(this, "El detalle no puede superar los 128 caracteres.")
                return false
            }
        }
        
        if (movementTypeText == "Venta" && binding.shippingCheckBox.isChecked) {
            if (selectedAddress == null) {
                CustomToast.showError(this, "Debe seleccionar una dirección de envío.")
                return false
            }
            
            // Validate that shipping cost has been calculated
            if (calculatedShippingCost <= 0) {
                CustomToast.showError(this, "Debe calcular el costo de envío antes de guardar.")
                return false
            }
            
            // Validate that final shipping cost is set
            val finalCostStr = binding.finalShippingCostInput.text.toString().trim()
            if (finalCostStr.isEmpty()) {
                CustomToast.showError(this, "El costo final de envío es obligatorio.")
                return false
            }
            val finalCost = finalCostStr.toDoubleOrNull() ?: 0.0
            if (finalCost <= 0) {
                CustomToast.showError(this, "El costo final de envío debe ser mayor a 0.")
                return false
            }
            
            if (selectedDeliveryDate == null) {
                CustomToast.showError(this, "La fecha de entrega es obligatoria cuando tiene envío.")
                return false
            }
        }
        return true
    }

    /**
     * Extracts a Movement object from the input fields.
     */
    private fun getMovementFromInputs(): Movement {
        android.util.Log.d("MovementEdit", "getMovementFromInputs() called")
        
        val selectedPersonName = binding.personSpinner.text.toString()
        val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
        val movementTypeText = binding.movementTypeSpinner.text.toString()
        val movementType = if (movementTypeText == "Venta") EMovementType.SALE else EMovementType.PURCHASE
        val totalAmount = binding.totalAmountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
        
        android.util.Log.d("MovementEdit", "Movement data - person: $selectedPersonName, type: $movementTypeText, totalAmount: $totalAmount")
        android.util.Log.d("MovementEdit", "Selected address: ${selectedAddress?.id}, label: ${selectedAddress?.label}")
        android.util.Log.d("MovementEdit", "Shipping checkbox checked: ${binding.shippingCheckBox.isChecked}")
        val shipment = if (movementType == EMovementType.SALE) {
            if (binding.shippingCheckBox.isChecked && selectedAddress != null) {
                val finalShippingCost =
                    binding.finalShippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                val shipmentData = Shipment(
                    addressId = selectedAddress!!.id,
                    formattedAddress = selectedAddress!!.formattedAddress,
                    lat = selectedAddress!!.latitude ?: 0.0,
                    lng = selectedAddress!!.longitude ?: 0.0,
                    calculatedCost = calculatedShippingCost,
                    cost = finalShippingCost,
                    date = selectedDeliveryDate,
                    status = if (currentMovement == null) EShipmentStatus.PENDING else (currentMovement!!.shipment?.status ?: EShipmentStatus.PENDING)
                )
                android.util.Log.d("MovementEdit", "Creating shipment with addressId: ${shipmentData.addressId}, calculatedCost: ${shipmentData.calculatedCost}, cost: ${shipmentData.cost}, status: ${shipmentData.status}")
                shipmentData
            } else {
                null // Save as null when checkbox is unchecked
            }
        } else null
        
        val itemsToSave = movementItems.toMutableList()
        if (movementType == EMovementType.SALE) {
            val discount = binding.discountInput.text.toString().toDoubleOrNull() ?: 0.0
            if (discount > 0) {
                val discountItem = MovementItem("custom", "discount", "discount", -discount, 1.0)
                itemsToSave.add(discountItem)
            }
        }
        
        val detail = if (movementType == EMovementType.SALE) {
            binding.detailInput.text.toString().trim()
        } else {
            ""
        }
        
        return Movement(
            id = currentMovement?.id ?: "",
            type = movementType,
            personId = selectedPerson?.id ?: "",
            movementDate = selectedDate,
            totalAmount = totalAmount,
            items = itemsToSave,
            shipment = shipment,
            detail = detail
        )
    }

    /**
     * Saves the movement (create or update) and cascades product updates to recipes.
     */
    private fun saveMovement() {
        android.util.Log.d("MovementEdit", "saveMovement() called")
        
        if (!validateInputs()) {
            android.util.Log.w("MovementEdit", "Validation failed, not saving movement")
            return
        }
        
        android.util.Log.d("MovementEdit", "Validation passed, proceeding to save")
        customLoader.show()
        
        val movementToSave = getMovementFromInputs()
        android.util.Log.d("MovementEdit", "Movement object created, shipment: ${movementToSave.shipment}")
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

    /**
     * Builds search results based on movement type and filters products with valid salePrice.
     */
    private fun buildSearchResults(products: List<com.estaciondulce.app.models.Product>, recipes: List<com.estaciondulce.app.models.Recipe>, isPurchase: Boolean): List<com.estaciondulce.app.models.ItemSearchResult> {
        val results = mutableListOf<com.estaciondulce.app.models.ItemSearchResult>()
        
        if (isPurchase) {
            // For purchases: show ALL products (no salePrice filter needed)
            val sortedProducts = products.sortedBy { it.name }
            for (product in sortedProducts) {
                results.add(com.estaciondulce.app.models.ItemSearchResult(
                    id = product.id,
                    name = product.name,
                    type = com.estaciondulce.app.models.ItemType.PRODUCT,
                    price = product.cost,
                    collection = "products",
                    collectionId = product.id
                ))
            }
        } else {
            // For sales: show recipes + products with salePrice > 0
            // Add custom item option for sales
            results.add(com.estaciondulce.app.models.ItemSearchResult(
                id = "custom",
                name = "Personalizar item",
                type = com.estaciondulce.app.models.ItemType.PRODUCT, // Use PRODUCT type for custom items
                price = 0.0,
                collection = "custom",
                collectionId = ""
            ))
            
            val sortedRecipes = recipes.sortedBy { it.name }
            for (recipe in sortedRecipes) {
                results.add(com.estaciondulce.app.models.ItemSearchResult(
                    id = recipe.id,
                    name = recipe.name,
                    type = com.estaciondulce.app.models.ItemType.RECIPE,
                    price = recipe.salePrice,
                    collection = "recipes",
                    collectionId = recipe.id
                ))
            }
            
            // Add products with salePrice > 0
            val productsWithSalePrice = products.filter { product ->
                product.salePrice > 0
            }.sortedBy { it.name }
            
            for (product in productsWithSalePrice) {
                results.add(com.estaciondulce.app.models.ItemSearchResult(
                    id = product.id,
                    name = product.name,
                    type = com.estaciondulce.app.models.ItemType.PRODUCT,
                    price = product.salePrice,
                    collection = "products",
                    collectionId = product.id
                ))
            }
        }
        
        return results
    }

}