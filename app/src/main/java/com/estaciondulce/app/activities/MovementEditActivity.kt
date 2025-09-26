package com.estaciondulce.app.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.adapters.MovementItemsAdapter
import com.estaciondulce.app.adapters.DialogAddItemAdapter
import com.estaciondulce.app.adapters.PersonSearchAdapter
import com.estaciondulce.app.adapters.AddressSelectionAdapter
import com.estaciondulce.app.adapters.RecipeImageAdapter
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.databinding.ActivityMovementEditBinding
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.helpers.DistanceMatrixHelper
import com.estaciondulce.app.helpers.StorageHelper
import com.estaciondulce.app.models.parcelables.Address
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.enums.EPersonType
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.MovementItem
import com.estaciondulce.app.models.parcelables.Person
import com.estaciondulce.app.models.parcelables.Delivery
import com.estaciondulce.app.models.parcelables.ShipmentDetails
import com.estaciondulce.app.models.parcelables.ShipmentSettings
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.parcelables.ItemSearchResult
import com.estaciondulce.app.models.enums.EItemType
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import java.io.File
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
    private lateinit var customLoader: CustomLoader
    private var currentMovement: Movement? = null
    private var originalMovement: Movement? = null // Copy of the original movement for kitchen order preservation
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
    private val distanceMatrixHelper = DistanceMatrixHelper()
    private var calculatedShippingCost: Double = 0.0
    private var selectedDeliveryType: String = EDeliveryType.PICKUP.name // Track selected delivery type
    
    companion object {
        private const val MAX_MOVEMENT_IMAGES = 3
    }
    private val storageHelper = StorageHelper()
    private var currentImageUris: List<Uri> = emptyList()
    private var existingImageUrls: MutableList<String> = mutableListOf()
    private var tempImageUrls: MutableList<String> = mutableListOf()
    private var tempImageFiles: MutableList<File> = mutableListOf()
    private val tempUrlToOriginalUriMap: MutableMap<String, Uri> = mutableMapOf()
    private val imagesToDeleteFromStorage: MutableList<String> = mutableListOf()
    private lateinit var referenceImageAdapter: RecipeImageAdapter
    private val sessionTempId = "temp_${System.currentTimeMillis()}"
    private val tempStorageUid: String
        get() = if (currentMovement?.id?.isNotEmpty() == true) currentMovement!!.id else sessionTempId

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val totalCurrentImages = existingImageUrls.size + tempImageUrls.size
            val availableSlots = MAX_MOVEMENT_IMAGES - totalCurrentImages
            val urisToProcess = uris.take(availableSlots)
            
            if (uris.size > availableSlots) {
                CustomToast.showInfo(this, "Solo puedes agregar $availableSlots imágenes más")
            }
            
            if (urisToProcess.isNotEmpty()) {
                currentImageUris = urisToProcess
                uploadImagesToTempStorage(urisToProcess)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageFiles.isNotEmpty()) {
            val tempFile = tempImageFiles.last()
            val uri = Uri.fromFile(tempFile)
            uploadImagesToTempStorage(listOf(uri))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        @Suppress("DEPRECATION")
        currentMovement = intent.getParcelableExtra<Movement>("MOVEMENT")
        originalMovement = currentMovement?.let { movement ->
            movement.copy(
                items = movement.items.map { it.copy() }
            )
        }
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
        
        referenceImageAdapter = RecipeImageAdapter { imageUrl ->
            deleteReferenceImage(imageUrl)
        }
        binding.referenceImagesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.referenceImagesRecyclerView.adapter = referenceImageAdapter
        binding.addReferenceImageButton.setOnClickListener { showReferenceImageSelectionDialog() }
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
                                binding.referenceImagesCard.visibility = View.GONE
                            }
                            EPersonType.CLIENT.dbValue -> {
                                binding.movementTypeSpinner.setText("Venta", false)
                                updatePersonSpinnerHint("Venta")
                                binding.shippingRow.visibility = View.VISIBLE
                                binding.discountCard.visibility = View.VISIBLE
                                binding.referenceImagesCard.visibility = View.VISIBLE
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
                    binding.referenceImagesCard.visibility = View.VISIBLE
                    binding.discountInput.setText("0")
                    discountAmount = 0.0
                    recalcTotalAmount()
                } else {
                    binding.shippingRow.visibility = View.GONE
                    binding.discountCard.visibility = View.GONE
                    binding.detailCard.visibility = View.GONE
                    binding.referenceImagesCard.visibility = View.GONE
                    binding.shippingAddressInput.setText("")
                    selectDeliveryType("pickup")
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
                binding.referenceImagesCard.visibility = View.VISIBLE
                
                movement.delivery?.let { delivery ->
                    val isShipment = delivery.type == EDeliveryType.SHIPMENT.name
                    selectDeliveryType(if (isShipment) EDeliveryType.SHIPMENT.name else EDeliveryType.PICKUP.name)
                    
                    selectedDeliveryDate = delivery.date
                    binding.deliveryDateTimeInput.setText(formatDateTime(delivery.date))
                    
                    if (isShipment) {
                        val shippingCost = delivery.shipment?.cost ?: 0.0
                        val calculatedCost = delivery.shipment?.calculatedCost ?: 0.0
                        
                        binding.finalShippingCostInput.setText(String.format("%.2f", shippingCost))
                        calculatedShippingCost = calculatedCost
                        
                        if (delivery.shipment?.addressId?.isNotEmpty() == true) {
                            val movementPersonId = movement.personId
                            addressesHelper.getAddressesForPerson(
                                personId = movementPersonId,
                                onSuccess = { addresses ->
                                    val address = addresses.find { it.id == delivery.shipment.addressId }
                                    if (address != null) {
                                        selectedAddress = address
                                        binding.shippingAddressInput.setText("${address.label}: ${address.formattedAddress}")
                                    } else {
                                        val fallbackText = delivery.shipment.formattedAddress
                                        binding.shippingAddressInput.setText(fallbackText)
                                        CustomToast.showWarning(this@MovementEditActivity, "Dirección original no encontrada en la lista actual")
                                    }
                                },
                                onError = { _ ->
                                    val fallbackText = delivery.shipment.formattedAddress
                                    binding.shippingAddressInput.setText(fallbackText)
                                    CustomToast.showError(this@MovementEditActivity, "Error al cargar direcciones guardadas")
                                }
                            )
                        }
                    }
                } ?: run {
                    clearDeliveryData()
                }
                
                val discountItem = movement.items.find { it.collection == "custom" && it.collectionId == "discount" }
                if (discountItem != null) {
                    binding.discountInput.setText((-discountItem.cost).toString())
                    discountAmount = -discountItem.cost
                } else {
                    binding.discountInput.setText("0")
                    discountAmount = 0.0
                }
                
                binding.detailInput.setText(movement.detail)
            } else {
                binding.shippingRow.visibility = View.GONE
                binding.discountCard.visibility = View.GONE
                binding.detailCard.visibility = View.GONE
                binding.referenceImagesCard.visibility = View.GONE
            }
            movementItems.clear()
            val regularItems = movement.items.filter { it.collection != "custom" || it.collectionId != "discount" }
            
            movementItems.addAll(regularItems)
            itemsAdapter.updateItems(movementItems.toMutableList())
            
            recalcTotalAmount()
            originalProductItems =
                movement.items.filter { it.collection == "products" }.map { it.copy() }
            
            if (movement.type == EMovementType.SALE) {
                existingImageUrls.clear()
                existingImageUrls.addAll(movement.referenceImages)
                updateReferenceImageGallery()
            }
        } ?: run {
            binding.dateInput.setText(formatDateTime(selectedDate))
        }
        setupDeliveryTypeSelector()
        
        
        FirestoreRepository.shipmentSettingsLiveData.observe(this) { _ ->
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
        @Suppress("DEPRECATION")
        val address = data?.getParcelableExtra<Address>("result_address")
            
            if (address != null) {
                val selectedPersonName = binding.personSpinner.text.toString()
                val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
                
                if (selectedPerson != null) {
                    
                    addressesHelper.addAddressToPerson(
                        personId = selectedPerson.id,
                        address = address,
                        onSuccess = { savedAddress ->
                            selectedAddress = savedAddress
                            val displayText = "${savedAddress.label}: ${savedAddress.formattedAddress}"
                            binding.shippingAddressInput.setText(displayText)
                            customLoader.hide()
                            CustomToast.showSuccess(this, "Nueva dirección agregada y seleccionada: ${savedAddress.label}")
                        },
                        onError = { exception ->
                            customLoader.hide()
                            CustomToast.showError(this, "Error al guardar la dirección: ${exception.message}")
                        }
                    )
                } else {
                    customLoader.hide()
                    CustomToast.showError(this, "Error: No se pudo encontrar la persona seleccionada.")
                }
            } else {
                customLoader.hide()
                CustomToast.showError(this, "Error al obtener la nueva dirección.")
            }
        } else if (requestCode == 2001 && resultCode == RESULT_CANCELED) {
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
        
        val selectedPersonName = binding.personSpinner.text.toString()
        
        if (selectedPersonName.isEmpty()) {
            CustomToast.showError(this, "Por favor, seleccione primero una persona.")
            return
        }

        val selectedPerson = personsList.find { "${it.name} ${it.lastName}" == selectedPersonName }
        
        if (selectedPerson == null) {
            CustomToast.showError(this, "No se pudo encontrar la persona seleccionada.")
            return
        }

        
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_address_selection, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.searchEditText)
        val addressesRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.estaciondulce.app.R.id.addressesRecyclerView)
        val emptyState = dialogView.findViewById<android.widget.LinearLayout>(com.estaciondulce.app.R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)

        addressesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        addressesHelper.getAddressesForPerson(
            personId = selectedPerson.id,
            onSuccess = { addresses ->
                addresses.forEach { _ ->
                }
                
                val sortedAddresses = addresses.sortedBy { it.label }
                
                val dialogAdapter = AddressSelectionAdapter(sortedAddresses) { selectedAddress ->
                    this.selectedAddress = selectedAddress
                    val displayText = "${selectedAddress.label}: ${selectedAddress.formattedAddress}"
                    binding.shippingAddressInput.setText(displayText)
                    
                    calculateShippingCostForAddress(selectedAddress)
                    
                    dialog.dismiss()
                }
                
                addressesRecyclerView.adapter = dialogAdapter

                if (sortedAddresses.isEmpty()) {
                    addressesRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    addressesRecyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }

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
                CustomToast.showError(this, "Error al cargar direcciones: ${exception.message}")
                addressesRecyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
        )


        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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


    /**
     * Sets up the delivery type selector with visual feedback.
     */
    private fun setupDeliveryTypeSelector() {
        if (currentMovement == null) {
            selectDeliveryType(EDeliveryType.PICKUP.name)
        }
        
        binding.pickupOptionCard.setOnClickListener {
            selectDeliveryType(EDeliveryType.PICKUP.name)
        }
        
        binding.shipmentOptionCard.setOnClickListener {
            selectDeliveryType(EDeliveryType.SHIPMENT.name)
        }
    }
    
    /**
     * Updates the visual state of delivery type selector and shows/hides appropriate fields.
     */
    private fun selectDeliveryType(type: String) {
        selectedDeliveryType = type
        
        if (type == "PICKUP") {
            binding.pickupOptionCard.setCardBackgroundColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.pickupOptionCard.setStrokeColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.pickupOptionCard.setStrokeWidth(2)
            binding.pickupTitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.pickupSubtitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.pickupIcon.setColorFilter(resources.getColor(com.estaciondulce.app.R.color.white, null))
            
            binding.shipmentOptionCard.setCardBackgroundColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.shipmentOptionCard.setStrokeColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.shipmentOptionCard.setStrokeWidth(1)
            binding.shipmentTitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            binding.shipmentSubtitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            binding.shipmentIcon.setColorFilter(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            
            binding.shippingAddressContainer.visibility = View.GONE
            binding.shippingDetailsContainer.visibility = View.VISIBLE
            binding.shippingCostLayout.visibility = View.GONE
            binding.deliveryDateTimeLayout.hint = "Fecha y hora de entrega"
            
            selectedAddress = null
            calculatedShippingCost = 0.0
            binding.shippingAddressInput.setText("")
            binding.finalShippingCostInput.setText("0.00")
            recalcTotalAmount()
            
        } else {
            binding.shipmentOptionCard.setCardBackgroundColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.shipmentOptionCard.setStrokeColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.shipmentOptionCard.setStrokeWidth(2)
            binding.shipmentTitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.shipmentSubtitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.shipmentIcon.setColorFilter(resources.getColor(com.estaciondulce.app.R.color.white, null))
            
            binding.pickupOptionCard.setCardBackgroundColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
            binding.pickupOptionCard.setStrokeColor(resources.getColor(com.estaciondulce.app.R.color.button_gradient_start, null))
            binding.pickupOptionCard.setStrokeWidth(1)
            binding.pickupTitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_primary, null))
            binding.pickupSubtitleText.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            binding.pickupIcon.setColorFilter(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            
            binding.shippingAddressContainer.visibility = View.VISIBLE
            binding.shippingDetailsContainer.visibility = View.VISIBLE
            binding.shippingCostLayout.visibility = View.VISIBLE
            binding.deliveryDateTimeLayout.hint = "Fecha y hora de envío"
        }
    }
    
    /**
     * Gets the currently selected delivery type.
     */
    private fun getSelectedDeliveryType(): String {
        return selectedDeliveryType
    }

    private fun formatDateTime(date: Date): String {
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("es"))
        val formatted = sdf.format(date)
        return formatted.replace("sept.", "sep")
    }


    /**
     * Recalculates shipping cost using Distance Matrix API.
     */
    private fun calculateShippingCostForAddress(address: Address) {
        if (address.latitude == null || address.longitude == null) {
            CustomToast.showError(this, "La dirección seleccionada no tiene coordenadas válidas")
            return
        }
        
        val settings = FirestoreRepository.shipmentSettingsLiveData.value
        if (settings == null || settings.baseAddress.isEmpty() || settings.fuelPrice <= 0 || settings.litersPerKm <= 0) {
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
                
                calculatedShippingCost = distanceMatrixHelper.calculateShippingCost(
                    distance, settings.fuelPrice, settings.litersPerKm
                )
                
                val formattedCost = String.format("%.2f", calculatedShippingCost)
                binding.finalShippingCostInput.setText(formattedCost)
                
                val formattedDistance = String.format("%.2f", distance)
                CustomToast.showSuccess(this, "Distancia: ${formattedDistance} km - Costo: $${formattedCost}")
                recalcTotalAmount()
            }
        }
    }

    /**
     * Clears all delivery data and resets UI elements.
     */
    private fun clearDeliveryData() {
        binding.shippingAddressContainer.visibility = View.VISIBLE
        binding.shippingDetailsContainer.visibility = View.VISIBLE
        binding.deliveryDateTimeInput.setText("")
        binding.deliveryDateTimeLayout.hint = "Fecha y hora de entrega"
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
            if (binding.shippingRow.visibility == View.VISIBLE && getSelectedDeliveryType() == EDeliveryType.SHIPMENT.name) {
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
                        EItemType.PRODUCT -> {
                            if (isPurchase) {
                                val cost = repository.productsLiveData.value?.find { it.id == selectedItem.id }?.cost ?: 0.0
                                if (cost <= 0.0) 1.0 else cost
                            } else {
                                selectedItem.price
                            }
                        }
                        EItemType.RECIPE -> {
                            val recipe = repository.recipesLiveData.value?.find { it.id == selectedItem.id }
                            val cost = if (isPurchase) recipe?.cost ?: 0.0 else recipe?.salePrice ?: 0.0
                            if (cost <= 0.0) 1.0 else cost
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
            
            val detail = binding.detailInput.text.toString().trim()
            if (detail.length > 512) {
                CustomToast.showError(this, "El detalle no puede superar los 512 caracteres.")
                return false
            }
        }
        
        if (movementTypeText == "Venta" && getSelectedDeliveryType() == EDeliveryType.SHIPMENT.name) {
            if (selectedAddress == null) {
                CustomToast.showError(this, "Debe seleccionar una dirección de envío.")
                return false
            }
            
            if (calculatedShippingCost <= 0) {
                CustomToast.showError(this, "Debe calcular el costo de envío antes de guardar.")
                return false
            }
            
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
        
        if (movementTypeText == "Venta" && selectedDeliveryDate == null) {
            CustomToast.showError(this, "La fecha de entrega es obligatoria.")
            return false
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
        
        val delivery = if (movementType == EMovementType.SALE) {
            val deliveryType = getSelectedDeliveryType()
            
            val deliveryData = if (deliveryType == EDeliveryType.SHIPMENT.name && selectedAddress != null) {
                val finalShippingCost =
                    binding.finalShippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                val shipmentDetails = ShipmentDetails(
                    addressId = selectedAddress!!.id,
                    formattedAddress = selectedAddress!!.formattedAddress,
                    lat = selectedAddress!!.latitude ?: 0.0,
                    lng = selectedAddress!!.longitude ?: 0.0,
                    cost = String.format("%.2f", finalShippingCost).toDouble(),
                    calculatedCost = String.format("%.2f", calculatedShippingCost).toDouble()
                )
                Delivery(
                    type = deliveryType,
                    date = selectedDeliveryDate ?: Date(),
                    status = if (currentMovement == null) "PENDING" else (currentMovement!!.delivery?.status ?: "PENDING"),
                    shipment = shipmentDetails
                )
            } else {
                Delivery(
                    type = deliveryType,
                    date = selectedDeliveryDate ?: Date(),
                    status = if (currentMovement == null) "PENDING" else (currentMovement!!.delivery?.status ?: "PENDING")
                )
            }
            deliveryData
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
            delivery = delivery,
            detail = detail,
            appliedAt = currentMovement?.appliedAt, // Preserve original appliedAt
            createdAt = currentMovement?.createdAt ?: Date() // Use original createdAt or current date for new movements
        )
    }

    /**
     * Saves the movement (create or update) and cascades product updates to recipes.
     */
    private fun saveMovement() {
        if (!validateInputs()) {
            return
        }
        
        customLoader.show()
        val movementToSave = getMovementFromInputs()
        
        if (currentMovement == null) {
            handleNewMovement(movementToSave)
        } else {
            handleMovementEdit(movementToSave)
        }
    }

    
    /**
     * Handles creation of a new movement
     */
    private fun handleNewMovement(movement: Movement) {
        MovementsHelper().addMovement(
            movement = movement,
            onSuccess = { newMovement ->
                if (movement.type == EMovementType.SALE && tempImageUrls.isNotEmpty()) {
                    uploadTempImagesToFinalLocation(
                        tempImageUrls = tempImageUrls,
                        movementId = newMovement.id,
                        onSuccess = { finalImageUrls ->
                            val finalMovement = newMovement.copy(referenceImages = finalImageUrls)
                            updateMovementWithFinalImages(finalMovement)
                        },
                        onError = { error ->
                            showErrorAndHideLoader("Error al subir imágenes: ${error.message}")
                        }
                    )
                } else {
                    showSuccessAndFinish("Movimiento agregado correctamente.")
                }
            },
            onError = { exception ->
                showErrorAndHideLoader("Error al agregar el movimiento: ${exception.message}")
            }
        )
    }
    
    /**
     * Updates movement with final image URLs
     */
    private fun updateMovementWithFinalImages(movement: Movement) {
        MovementsHelper().updateMovement(
            movementId = movement.id,
            movement = movement,
            updateKitchenOrders = false,
            onSuccess = {
                showSuccessAndFinish("Movimiento agregado correctamente.")
            },
            onError = { error ->
                showErrorAndHideLoader("Error al actualizar imágenes: ${error.message}")
            }
        )
    }
    
    
    /**
     * Handles editing of an existing movement
     */
    private fun handleMovementEdit(movement: Movement) {
        val originalMovementId = currentMovement!!.id
        
        MovementsHelper().addMovement(
            movement = movement,
            createKitchenOrders = false, // Don't create kitchen orders here - they will be handled by preserveKitchenOrdersForEditedMovement
            onSuccess = { newMovement ->
                if (movement.type == EMovementType.SALE && hasImageChanges()) {
                    handleImageOperationsForEdit(newMovement, originalMovementId)
                } else {
                    updateMovementAndCleanup(newMovement, originalMovementId)
                }
            },
            onError = { exception ->
                showErrorAndHideLoader("Error al crear el movimiento actualizado: ${exception.message}")
            }
        )
    }
    
    /**
     * Checks if there are any image changes in the movement
     */
    private fun hasImageChanges(): Boolean {
        return tempImageUrls.isNotEmpty() || 
               imagesToDeleteFromStorage.isNotEmpty() || 
               existingImageUrls.isNotEmpty()
    }
    
    
    /**
     * Handles all image operations for edited movements
     */
    private fun handleImageOperationsForEdit(newMovement: Movement, originalMovementId: String) {
        if (existingImageUrls.isNotEmpty()) {
            copyExistingImagesAndHandleNew(newMovement, originalMovementId)
        } else {
            handleNewImagesOnly(newMovement, originalMovementId)
        }
    }
    
    /**
     * Copies existing images and handles new ones
     */
    private fun copyExistingImagesAndHandleNew(newMovement: Movement, originalMovementId: String) {
        copyExistingImagesToNewBucket(
            existingImageUrls = existingImageUrls,
            newMovementId = newMovement.id,
            onSuccess = { copiedImageUrls ->
                if (tempImageUrls.isNotEmpty()) {
                    uploadNewImagesAndComplete(copiedImageUrls, newMovement, originalMovementId)
                } else {
                    completeMovementEdit(newMovement.copy(referenceImages = copiedImageUrls), originalMovementId)
                }
            },
            onError = { error ->
                showErrorAndHideLoader("Error al copiar imágenes existentes: ${error.message}")
            }
        )
    }
    
    /**
     * Handles only new images (no existing ones to copy)
     */
    private fun handleNewImagesOnly(newMovement: Movement, originalMovementId: String) {
        if (tempImageUrls.isNotEmpty()) {
            uploadNewImagesAndComplete(emptyList(), newMovement, originalMovementId)
        } else {
            completeMovementEdit(newMovement.copy(referenceImages = emptyList()), originalMovementId)
        }
    }
    
    /**
     * Uploads new images and completes the movement edit
     */
    private fun uploadNewImagesAndComplete(existingImageUrls: List<String>, newMovement: Movement, originalMovementId: String) {
        uploadTempImagesToFinalLocation(
            tempImageUrls = tempImageUrls,
            movementId = newMovement.id,
            onSuccess = { newImageUrls ->
                val finalMovement = newMovement.copy(referenceImages = existingImageUrls + newImageUrls)
                completeMovementEdit(finalMovement, originalMovementId)
            },
            onError = { error ->
                showErrorAndHideLoader("Error al subir nuevas imágenes: ${error.message}")
            }
        )
    }
    
    
    /**
     * Completes the movement edit process
     */
    private fun completeMovementEdit(newMovement: Movement, originalMovementId: String) {
        updateMovementAndCleanup(newMovement, originalMovementId)
    }
    
    
    /**
     * Shows success message and finishes the activity
     */
    private fun showSuccessAndFinish(message: String) {
        customLoader.hide()
        CustomToast.showSuccess(this, message)
        finish()
    }
    
    /**
     * Shows error message and hides loader
     */
    private fun showErrorAndHideLoader(message: String) {
        customLoader.hide()
        CustomToast.showError(this, message)
    }

    /**
     * Updates the movement with final data and cleans up the original movement.
     */
    private fun updateMovementAndCleanup(newMovement: Movement, originalMovementId: String) {
        
        updateMovementAndCleanupInternal(newMovement, originalMovementId, shouldPreserveKitchenOrders = newMovement.type == EMovementType.SALE && originalMovement != null)
    }

    /**
     * Internal method to update movement and cleanup original
     */
    private fun updateMovementAndCleanupInternal(newMovement: Movement, originalMovementId: String, shouldPreserveKitchenOrders: Boolean = false) {
        MovementsHelper().updateMovement(
            movementId = newMovement.id,
            movement = newMovement,
            updateKitchenOrders = false, // Don't update kitchen orders here - they are handled by preserveKitchenOrdersForEditedMovement
            onSuccess = {
                if (shouldPreserveKitchenOrders && originalMovement != null) {
                    MovementsHelper().preserveKitchenOrdersForEditedMovement(
                        originalMovement = originalMovement!!,
                        newMovement = newMovement,
                        onSuccess = {
                            cleanupOriginalMovement(originalMovementId)
                        },
                        onError = { error ->
                            customLoader.hide()
                            CustomToast.showError(this, "Error al preservar órdenes de cocina: ${error.message}")
                        }
                    )
                } else {
                    cleanupOriginalMovement(originalMovementId)
                }
            },
            onError = { error ->
                customLoader.hide()
                CustomToast.showError(this, "Error al actualizar el movimiento: ${error.message}")
            }
        )
    }
    
    private fun cleanupOriginalMovement(originalMovementId: String) {
        MovementsHelper().deleteMovement(
            movementId = originalMovementId,
            onSuccess = {
                val allImagesToDelete = imagesToDeleteFromStorage.toMutableList()
                
                currentMovement?.referenceImages?.let { originalImages ->
                    allImagesToDelete.addAll(originalImages)
                }
                
                if (allImagesToDelete.isNotEmpty()) {
                    storageHelper.deleteImagesFromStorage(
                        imageUrls = allImagesToDelete,
                        onSuccess = {
                            showSuccessAndFinish("Movimiento actualizado correctamente.")
                        },
                        onError = { _ ->
                            showSuccessAndFinish("Movimiento actualizado correctamente.")
                        }
                    )
                } else {
                    showSuccessAndFinish("Movimiento actualizado correctamente.")
                }
            },
            onError = { _ ->
                showErrorAndHideLoader("Movimiento actualizado, pero hubo un error al eliminar el original. Contacte soporte.")
            }
        )
    }

    /**
     * Builds search results based on movement type and filters products with valid salePrice.
     */
    private fun buildSearchResults(products: List<Product>, recipes: List<Recipe>, isPurchase: Boolean): List<ItemSearchResult> {
        val results = mutableListOf<ItemSearchResult>()
        
        if (isPurchase) {
            val sortedProducts = products.sortedBy { it.name }
            for (product in sortedProducts) {
                results.add(ItemSearchResult(
                    id = product.id,
                    name = product.name,
                    type = EItemType.PRODUCT,
                    price = product.cost,
                    collection = "products",
                    collectionId = product.id
                ))
            }
        } else {
            results.add(ItemSearchResult(
                id = "custom",
                name = "Personalizar item",
                type = EItemType.PRODUCT, // Use PRODUCT type for custom items
                price = 0.0,
                collection = "custom",
                collectionId = ""
            ))
            
            val sortedRecipes = recipes.sortedBy { it.name }
            for (recipe in sortedRecipes) {
                results.add(ItemSearchResult(
                    id = recipe.id,
                    name = recipe.name,
                    type = EItemType.RECIPE,
                    price = recipe.salePrice,
                    collection = "recipes",
                    collectionId = recipe.id
                ))
            }
            
            val productsWithSalePrice = products.filter { product ->
                product.salePrice > 0
            }.sortedBy { it.name }
            
            for (product in productsWithSalePrice) {
                results.add(ItemSearchResult(
                    id = product.id,
                    name = product.name,
                    type = EItemType.PRODUCT,
                    price = product.salePrice,
                    collection = "products",
                    collectionId = product.id
                ))
            }
        }
        
        return results
    }

    /**
     * Shows the reference image selection dialog.
     */
    private fun showReferenceImageSelectionDialog() {
        val totalCurrentImages = existingImageUrls.size + tempImageUrls.size
        if (totalCurrentImages >= MAX_MOVEMENT_IMAGES) {
            CustomToast.showInfo(this, "Máximo de $MAX_MOVEMENT_IMAGES imágenes alcanzado")
            return
        }

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        options.add("Galería")
        actions.add { selectImagesFromGallery() }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which]()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Takes a picture using the camera.
     */
    private fun takePictureWithCamera() {
        try {
            val tempFile = File.createTempFile("movement_image_${System.currentTimeMillis()}", ".jpg", cacheDir)
            tempImageFiles.add(tempFile)
            val uri = Uri.fromFile(tempFile)
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            CustomToast.showError(this, "Error al crear archivo temporal: ${e.message}")
        }
    }

    /**
     * Selects images from gallery.
     */
    private fun selectImagesFromGallery() {
        val availableSlots = MAX_MOVEMENT_IMAGES - (existingImageUrls.size + tempImageUrls.size)
        if (availableSlots <= 0) {
            CustomToast.showInfo(this, "Máximo de $MAX_MOVEMENT_IMAGES imágenes alcanzado")
            return
        }
        galleryLauncher.launch("image/*")
    }

    /**
     * Updates the reference image gallery UI.
     */
    private fun updateReferenceImageGallery() {
        val allImages = existingImageUrls + tempImageUrls
        referenceImageAdapter.updateImages(allImages)
        val totalImages = allImages.size
        binding.addReferenceImageButton.isEnabled = totalImages < MAX_MOVEMENT_IMAGES
        binding.addReferenceImageButton.text = if (totalImages < MAX_MOVEMENT_IMAGES) {
            "Agregar imagen ($totalImages/$MAX_MOVEMENT_IMAGES)"
        } else {
            "Máximo alcanzado ($MAX_MOVEMENT_IMAGES/$MAX_MOVEMENT_IMAGES)"
        }
    }

    /**
     * Deletes a reference image.
     */
    private fun deleteReferenceImage(imageUrl: String) {
        if (existingImageUrls.contains(imageUrl)) {
            existingImageUrls.remove(imageUrl)
            imagesToDeleteFromStorage.add(imageUrl)
        } else if (tempImageUrls.contains(imageUrl)) {
            tempImageUrls.remove(imageUrl)
            tempUrlToOriginalUriMap.remove(imageUrl)
        }
        updateReferenceImageGallery()
    }

    /**
     * Uploads images to temporary storage.
     */
    private fun uploadImagesToTempStorage(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        customLoader.show()
        
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        var completedUploads = 0
        val totalUploads = uris.size
        
        uris.forEachIndexed { index, uri ->
            val fileName = if (uris.size > 1) {
                "${timestamp}-${index + 1}.jpg"
            } else {
                "${timestamp}.jpg"
            }
            
            storageHelper.uploadTempImage(
                imageUri = uri,
                uid = tempStorageUid,
                fileName = fileName,
                onSuccess = { downloadUrl ->
                    tempImageUrls.add(downloadUrl)
                    tempUrlToOriginalUriMap[downloadUrl] = uri
                    completedUploads++
                    
                    if (completedUploads == totalUploads) {
                        customLoader.hide()
                        updateReferenceImageGallery()
                        CustomToast.showSuccess(this, "Imágenes subidas exitosamente")
                    }
                },
                onError = { error ->
                    completedUploads++
                    if (completedUploads == totalUploads) {
                        customLoader.hide()
                        updateReferenceImageGallery()
                        CustomToast.showError(this, "Error al subir algunas imágenes: ${error.message}")
                    }
                }
            )
        }
    }


    /**
     * Uploads temp images to final location for a movement.
     */
    private fun uploadTempImagesToFinalLocation(
        tempImageUrls: List<String>,
        movementId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (tempImageUrls.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        
        customLoader.show()
        
        val urisToUpload = mutableListOf<Uri>()
        val fileNames = mutableListOf<String>()
        
        tempImageUrls.forEach { tempUrl ->
            val originalUri = findOriginalUriForTempUrl(tempUrl)
            if (originalUri != null) {
                urisToUpload.add(originalUri)
                val fileName = tempUrl.substringAfterLast("/").substringBefore("?")
                    .replace("%2F", "/")
                    .substringAfterLast("/")
                fileNames.add(fileName)
            }
        }
        
        if (urisToUpload.isEmpty()) {
            customLoader.hide()
            onError(Exception("No se encontraron las URIs originales para las imágenes temp"))
            return
        }
        
        var completedUploads = 0
        val totalUploads = urisToUpload.size
        val finalImageUrls = mutableListOf<String>()
        
        urisToUpload.forEachIndexed { index, uri ->
            val fileName = fileNames[index]
            
            storageHelper.uploadMovementImageWithName(
                imageUri = uri,
                movementId = movementId,
                fileName = fileName,
                onSuccess = { downloadUrl ->
                    finalImageUrls.add(downloadUrl)
                    completedUploads++
                    
                    if (completedUploads == totalUploads) {
                        customLoader.hide()
                        onSuccess(finalImageUrls)
                    }
                },
                onError = { error ->
                    customLoader.hide()
                    onError(error)
                }
            )
        }
    }

    /**
     * Finds the original URI for a temporary URL.
     */
    private fun findOriginalUriForTempUrl(tempUrl: String): Uri? {
        return tempUrlToOriginalUriMap[tempUrl]
    }

    /**
     * Copies existing images to the new movement bucket.
     */
    private fun copyExistingImagesToNewBucket(
        existingImageUrls: List<String>,
        newMovementId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (existingImageUrls.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        
        customLoader.show()
        
        var completedCopies = 0
        val totalCopies = existingImageUrls.size
        val copiedImageUrls = mutableListOf<String>()
        var hasError = false
        
        existingImageUrls.forEachIndexed { index, existingImageUrl ->
            val fileName = extractFileNameFromUrl(existingImageUrl) ?: run {
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                if (existingImageUrls.size > 1) {
                    "${timestamp}-${index + 1}.jpg"
                } else {
                    "${timestamp}.jpg"
                }
            }
            
            storageHelper.copyMovementImage(
                sourceImageUrl = existingImageUrl,
                newMovementId = newMovementId,
                fileName = fileName,
                onSuccess = { newImageUrl ->
                    copiedImageUrls.add(newImageUrl)
                    completedCopies++
                    
                    if (completedCopies == totalCopies) {
                        customLoader.hide()
                        if (hasError) {
                            onError(Exception("Some images could not be copied"))
                        } else {
                            onSuccess(copiedImageUrls)
                        }
                    }
                },
                onError = { error ->
                    completedCopies++
                    hasError = true
                    if (completedCopies == totalCopies) {
                        customLoader.hide()
                        onError(Exception("Error copying images: ${error.message}"))
                    }
                }
            )
        }
    }

    /**
     * Extracts filename from a Firebase Storage URL.
     */
    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val pathSegments = uri.pathSegments
            if (pathSegments.isNotEmpty()) {
                val fileName = pathSegments.last()
                val cleanFileName = fileName.substringBefore("?")
                
                if (cleanFileName.contains("/")) {
                    cleanFileName.substringAfterLast("/")
                } else {
                    cleanFileName
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

}
