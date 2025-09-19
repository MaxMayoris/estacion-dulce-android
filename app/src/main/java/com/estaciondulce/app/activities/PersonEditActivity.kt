package com.estaciondulce.app.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityPersonEditBinding
import com.estaciondulce.app.helpers.PersonsHelper
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.models.parcelables.Person
import com.estaciondulce.app.models.parcelables.Phone
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.Address
import com.estaciondulce.app.models.enums.EPersonType
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.adapters.MovementAdapter
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.helpers.MovementsHelper
import android.view.LayoutInflater
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Activity para agregar o editar una persona (Cliente o Proveedor).
 */
class PersonEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonEditBinding
    private val personsHelper = PersonsHelper() // Usamos el helper para personas
    private val addressesHelper = AddressesHelper()
    private lateinit var customLoader: CustomLoader
    private var currentPerson: Person? = null
    private val phoneItems = mutableListOf<android.view.View>()
    private val addressItems = mutableListOf<android.view.View>()
    private var selectedTab = "information"
    private val repository = FirestoreRepository
    private var tabsVisible = false
    private var emptyMessageView: android.widget.TextView? = null
    
    // Draft system for addresses - changes are kept in memory until save
    private val originalAddresses = mutableListOf<Address>()
    private val newAddresses = mutableListOf<Address>()
    private val editedAddresses = mutableListOf<Address>()
    private val deletedAddressIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        @Suppress("DEPRECATION")
        currentPerson = intent.getParcelableExtra<Person>("PERSON")
        supportActionBar?.title = if (currentPerson != null) "Editar Persona" else "Agregar Persona"

        val personTypes = EPersonType.values().map { it.displayValue }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, personTypes)
        binding.personTypeSpinner.setAdapter(spinnerAdapter)

        // Initialize phones
        currentPerson?.let { person ->
            binding.personNameInput.setText(person.name)
            binding.personLastNameInput.setText(person.lastName)
            
            // Load existing phones
            person.phones.forEach { phone ->
                addPhoneDisplayItem(phone.phoneNumberPrefix, phone.phoneNumberSuffix)
            }
            
            val typeText = EPersonType.getDisplayValue(person.type)
            binding.personTypeSpinner.setText(typeText, false)
        }

        binding.savePersonButton.setOnClickListener { savePerson() }
        binding.addPhoneButton.setOnClickListener { showAddPhoneDialog() }
        binding.addAddressButton.setOnClickListener { showAddAddressDialog() }
        
        // Set up tabs
        binding.informationTab.setOnClickListener { selectTab("information") }
        binding.movementsTab.setOnClickListener { selectTab("movements") }
        
        // Set up movements functionality
        binding.addMovementButton.setOnClickListener { addMovement() }
        
        // Initialize tab visibility based on person state
        initializeTabVisibility()
        
        // Load movements and addresses if editing existing person
        currentPerson?.let { person ->
            if (person.id.isNotEmpty()) {
                loadMovements(person.id)
                loadAddresses(person.id)
            }
        }
    }

    /**
     * Initializes tab visibility based on whether the person is new or existing.
     */
    private fun initializeTabVisibility() {
        val isNewPerson = currentPerson == null || currentPerson?.id?.isEmpty() == true
        
        if (isNewPerson) {
            // Hide tabs for new persons
            tabsVisible = false
            binding.informationTab.visibility = View.GONE
            binding.movementsTab.visibility = View.GONE
            binding.informationTabContent.visibility = View.VISIBLE
            binding.movementsTabContent.visibility = View.GONE
        } else {
            // Show tabs for existing persons
            tabsVisible = true
            binding.informationTab.visibility = View.VISIBLE
            binding.movementsTab.visibility = View.VISIBLE
            selectTab("information") // Default to information tab
        }
    }

    /**
     * Selects a tab and updates the UI accordingly.
     */
    private fun selectTab(tab: String) {
        selectedTab = tab
        
        when (tab) {
            "information" -> {
                // Update tab appearance
                binding.informationTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.informationTab.setTextColor(getColor(com.estaciondulce.app.R.color.white))
                binding.movementsTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.movementsTab.setTextColor(getColor(com.estaciondulce.app.R.color.text_secondary))
                
                // Show/hide content
                binding.informationTabContent.visibility = View.VISIBLE
                binding.movementsTabContent.visibility = View.GONE
            }
            "movements" -> {
                // Update tab appearance
                binding.movementsTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.movementsTab.setTextColor(getColor(com.estaciondulce.app.R.color.white))
                binding.informationTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.informationTab.setTextColor(getColor(com.estaciondulce.app.R.color.text_secondary))
                
                // Show/hide content
                binding.informationTabContent.visibility = View.GONE
                binding.movementsTabContent.visibility = View.VISIBLE
                
                // Load movements if not already loaded
                currentPerson?.let { person ->
                    if (person.id.isNotEmpty()) {
                        loadMovements(person.id)
                    }
                }
            }
        }
    }

    /**
     * Loads movements for the current person.
     */
    private fun loadMovements(personId: String) {
        val movements = repository.movementsLiveData.value?.filter { movement -> movement.personId == personId } ?: emptyList()
        setupMovementsTable(movements)
    }

    /**
     * Sets up the movements table.
     */
    private fun setupMovementsTable(movements: List<Movement>) {
        val sortedMovements = movements.sortedByDescending { it.movementDate }
        
        if (sortedMovements.isEmpty()) {
            // Show empty state message
            binding.movementsTable.visibility = View.GONE
            showEmptyMovementsMessage()
        } else {
            // Hide empty message and show table
            hideEmptyMovementsMessage()
            binding.movementsTable.visibility = View.VISIBLE
            
            val columnConfigs = listOf("Fecha", "Monto").toColumnConfigs(currencyColumns = setOf(1))
            
            binding.movementsTable.setupTableWithConfigs(
                columnConfigs = columnConfigs,
                data = sortedMovements,
                adapter = MovementAdapter(
                    movementList = sortedMovements,
                    onRowClick = { movement -> editMovement(movement) },
                    onDeleteClick = { movement -> deleteMovement(movement) }
                ) { movement ->
                    val dateString = formatDateToSpanish(movement.movementDate)
                    listOf(dateString, movement.totalAmount)
                },
                pageSize = 10,
                columnValueGetter = { item, columnIndex ->
                    val movement = item as Movement
                    when (columnIndex) {
                        0 -> formatDateToSpanish(movement.movementDate)
                        1 -> movement.totalAmount
                        else -> null
                    }
                },
                enableColumnSorting = false
            )
        }
    }

    /**
     * Formats date to Spanish format: "dd mes hh:mm"
     */
    private fun formatDateToSpanish(date: java.util.Date): String {
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("es"))
        val formatted = sdf.format(date)
        return formatted.replace("sept.", "sep")
            .replace("enero", "ene")
            .replace("febrero", "feb")
            .replace("marzo", "mar")
            .replace("abril", "abr")
            .replace("mayo", "may")
            .replace("junio", "jun")
            .replace("julio", "jul")
            .replace("agosto", "ago")
            .replace("octubre", "oct")
            .replace("noviembre", "nov")
            .replace("diciembre", "dic")
    }

    /**
     * Shows the empty movements message.
     */
    private fun showEmptyMovementsMessage() {
        if (emptyMessageView == null) {
            emptyMessageView = android.widget.TextView(this).apply {
                text = "Sin Movimientos"
                textSize = 16f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(getColor(com.estaciondulce.app.R.color.text_secondary))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                    topMargin = 64.dpToPx()
                }
            }
            binding.movementsTabContent.addView(emptyMessageView, 0) // Add at the beginning
        }
    }

    /**
     * Hides the empty movements message.
     */
    private fun hideEmptyMovementsMessage() {
        emptyMessageView?.let { messageView ->
            binding.movementsTabContent.removeView(messageView)
            emptyMessageView = null
        }
    }

    /**
     * Extension function to convert dp to pixels.
     */
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    /**
     * Adds a new movement for the current person.
     */
    private fun addMovement() {
        currentPerson?.let { person ->
            val intent = Intent(this, MovementEditActivity::class.java)
            intent.putExtra("PERSON_ID", person.id)
            startActivity(intent)
        } ?: run {
            CustomToast.showError(this, "Debe guardar la persona primero antes de agregar movimientos.")
        }
    }

    /**
     * Edits an existing movement.
     */
    private fun editMovement(movement: Movement) {
        val intent = Intent(this, MovementEditActivity::class.java)
        intent.putExtra("MOVEMENT", movement)
        startActivity(intent)
    }

    /**
     * Deletes a movement.
     */
    private fun deleteMovement(movement: Movement) {
        val movementType = if (movement.type == com.estaciondulce.app.models.enums.EMovementType.PURCHASE) "Compra" else "Venta"
        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
        val personName = person?.let { "${it.name} ${it.lastName}" } ?: "Persona desconocida"
        val formattedAmount = String.format("%.2f", movement.totalAmount)
        
        DeleteConfirmationDialog.show(
            context = this,
            itemName = "$movementType a $personName por $${formattedAmount}",
            itemType = "movimiento",
            onConfirm = {
                customLoader.show()
                MovementsHelper().deleteMovement(
                    movementId = movement.id,
                    onSuccess = {
                        customLoader.hide()
                        CustomToast.showSuccess(this, "$movementType a $personName eliminada correctamente.")
                        // Reload movements
                        currentPerson?.let { person ->
                            if (person.id.isNotEmpty()) {
                                loadMovements(person.id)
                            }
                        }
                    },
                    onError = {
                        customLoader.hide()
                        CustomToast.showError(this, "Error al eliminar el movimiento.")
                    }
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Reload movements and addresses when returning from other activities
        currentPerson?.let { person ->
            if (person.id.isNotEmpty()) {
                loadMovements(person.id)
                loadAddresses(person.id)
            }
        }
    }

    /**
     * Shows the add phone dialog.
     */
    private fun showAddPhoneDialog() {
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_phone, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val provinceSpinner = dialogView.findViewById<android.widget.AutoCompleteTextView>(com.estaciondulce.app.R.id.provinceSpinner)
        val phoneNumberInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.phoneNumberInput)
        val addPhoneButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.addPhoneButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.cancelButton)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)

        // Set up province spinner
        val phonePrefixOptions = listOf(
            "Córdoba (351)",
            "Santa Fe (341)",
            "Mendoza (261)",
            "Tucumán (381)",
            "Salta (387)",
            "Jujuy (388)",
            "Santiago del Estero (386)",
            "Corrientes (379)",
            "Entre Ríos (343)",
            "Misiones (375)",
            "Chaco (373)",
            "Formosa (370)",
            "San Juan (264)",
            "San Luis (266)",
            "La Rioja (382)",
            "Neuquén (299)",
            "Río Negro (294)",
            "Chubut (297)",
            "Santa Cruz (296)",
            "Tierra del Fuego (291)"
        )
        val phonePrefixAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, phonePrefixOptions)
        provinceSpinner.setAdapter(phonePrefixAdapter)

        val defaultPrefix = phonePrefixOptions.find { it.contains("San Juan") }
        if (defaultPrefix != null) {
            provinceSpinner.setText(defaultPrefix, false)
        }

        // Set up buttons
        addPhoneButton.setOnClickListener {
            val selectedProvince = provinceSpinner.text.toString()
            val phoneNumber = phoneNumberInput.text.toString().trim()

            if (selectedProvince.isEmpty()) {
                CustomToast.showError(this, "Seleccione una provincia.")
                return@setOnClickListener
            }

            if (phoneNumber.isEmpty()) {
                CustomToast.showError(this, "Ingrese el número de teléfono.")
                return@setOnClickListener
            }

            val phonePrefix = selectedProvince.substringAfter("(").substringBefore(")")
            addPhoneDisplayItem(phonePrefix, phoneNumber)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener { dialog.dismiss() }
        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Adds a phone display item to the phones container.
     */
    private fun addPhoneDisplayItem(prefix: String, suffix: String) {
        val phoneDisplayView = layoutInflater.inflate(com.estaciondulce.app.R.layout.item_phone_display, binding.phonesContainer, false)
        
        val phoneNumberText = phoneDisplayView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.phoneNumberText)
        val copyButton = phoneDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.copyPhoneButton)
        val deleteButton = phoneDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.deletePhoneButton)
        
        // Set phone number text (prefix is the area code, add space before suffix)
        phoneNumberText.text = "${prefix} ${suffix}"
        
        // Set up copy button
        copyButton.setOnClickListener {
            copyPhoneNumber(prefix, suffix)
        }
        
        // Set up phone number click for editing
        phoneNumberText.setOnClickListener {
            showEditPhoneDialog(phoneDisplayView, prefix, suffix)
        }
        
        // Set up delete button
        deleteButton.setOnClickListener {
            showDeletePhoneConfirmation(phoneDisplayView)
        }
        
        // Add to container and track it
        binding.phonesContainer.addView(phoneDisplayView)
        phoneItems.add(phoneDisplayView)
    }

    /**
     * Copies phone number to clipboard.
     */
    private fun copyPhoneNumber(prefix: String, suffix: String) {
        val fullNumber = "${prefix}${suffix}"
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Número de teléfono", fullNumber)
        clipboard.setPrimaryClip(clip)
        
        CustomToast.showSuccess(this, "Número copiado: $fullNumber")
    }

    /**
     * Shows edit phone dialog with pre-filled values.
     */
    private fun showEditPhoneDialog(phoneDisplayView: android.view.View, currentPrefix: String, currentSuffix: String) {
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_phone, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val provinceSpinner = dialogView.findViewById<android.widget.AutoCompleteTextView>(com.estaciondulce.app.R.id.provinceSpinner)
        val phoneNumberInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.phoneNumberInput)
        val addPhoneButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.addPhoneButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.cancelButton)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)

        // Change dialog title and button text for edit mode
        dialog.setTitle("Editar Teléfono")

        // Set up province spinner
        val phonePrefixOptions = listOf(
            "Córdoba (351)",
            "Santa Fe (341)",
            "Mendoza (261)",
            "Tucumán (381)",
            "Salta (387)",
            "Jujuy (388)",
            "Santiago del Estero (386)",
            "Corrientes (379)",
            "Entre Ríos (343)",
            "Misiones (375)",
            "Chaco (373)",
            "Formosa (370)",
            "San Juan (264)",
            "San Luis (266)",
            "La Rioja (382)",
            "Neuquén (299)",
            "Río Negro (294)",
            "Chubut (297)",
            "Santa Cruz (296)",
            "Tierra del Fuego (291)"
        )
        val phonePrefixAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, phonePrefixOptions)
        provinceSpinner.setAdapter(phonePrefixAdapter)

        // Pre-fill current values
        val currentProvince = phonePrefixOptions.find { it.contains("($currentPrefix)") }
        if (currentProvince != null) {
            provinceSpinner.setText(currentProvince, false)
        }
        phoneNumberInput.setText(currentSuffix)
        addPhoneButton.text = "Actualizar"

        // Set up buttons
        addPhoneButton.setOnClickListener {
            val selectedProvince = provinceSpinner.text.toString()
            val phoneNumber = phoneNumberInput.text.toString().trim()

            if (selectedProvince.isEmpty()) {
                CustomToast.showError(this, "Seleccione una provincia.")
                return@setOnClickListener
            }

            if (phoneNumber.isEmpty()) {
                CustomToast.showError(this, "Ingrese el número de teléfono.")
                return@setOnClickListener
            }

            val phonePrefix = selectedProvince.substringAfter("(").substringBefore(")")
            
            // Update the existing phone display
            updatePhoneDisplayItem(phoneDisplayView, phonePrefix, phoneNumber)
            
            dialog.dismiss()
        }

        cancelButton.setOnClickListener { dialog.dismiss() }
        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Updates an existing phone display item.
     */
    private fun updatePhoneDisplayItem(phoneDisplayView: android.view.View, prefix: String, suffix: String) {
        val phoneNumberText = phoneDisplayView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.phoneNumberText)
        phoneNumberText.text = "${prefix} ${suffix}"
        
        // Update the copy button to use new values
        val copyButton = phoneDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.copyPhoneButton)
        copyButton.setOnClickListener {
            copyPhoneNumber(prefix, suffix)
        }
        
        // Update the edit click to use new values
        phoneNumberText.setOnClickListener {
            showEditPhoneDialog(phoneDisplayView, prefix, suffix)
        }
        
        CustomToast.showSuccess(this, "Teléfono actualizado correctamente.")
    }

    /**
     * Shows confirmation dialog before deleting a phone.
     */
    private fun showDeletePhoneConfirmation(phoneDisplayView: android.view.View) {
        // Don't allow removing if it's the only phone
        if (phoneItems.size <= 1) {
            CustomToast.showError(this, "Debe tener al menos un teléfono.")
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Eliminar Teléfono")
            .setMessage("¿Está seguro que desea eliminar este teléfono?")
            .setPositiveButton("Eliminar") { _, _ ->
                binding.phonesContainer.removeView(phoneDisplayView)
                phoneItems.remove(phoneDisplayView)
                CustomToast.showSuccess(this, "Teléfono eliminado correctamente.")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Loads addresses for the current person.
     */
    private fun loadAddresses(personId: String) {
        addressesHelper.getAddressesForPerson(
            personId = personId,
            onSuccess = { addresses ->
                // Store original addresses for draft system
                originalAddresses.clear()
                originalAddresses.addAll(addresses)
                setupAddressesList(getCurrentAddresses())
            },
            onError = { exception ->
                CustomToast.showError(this, "Error al cargar direcciones: ${exception.message}")
            }
        )
    }

    /**
     * Gets the current addresses combining original, new, and edited addresses, excluding deleted ones.
     */
    private fun getCurrentAddresses(): List<Address> {
        val currentAddresses = mutableListOf<Address>()
        
        // Add original addresses that haven't been deleted or edited
        originalAddresses.forEach { original ->
            if (!deletedAddressIds.contains(original.id)) {
                // Check if this address was edited
                val editedVersion = editedAddresses.find { it.id == original.id }
                if (editedVersion != null) {
                    currentAddresses.add(editedVersion)
                } else {
                    // Use original address (AddressPickerActivity now returns properly formatted addresses)
                    currentAddresses.add(original)
                }
            }
        }
        
        // Add new addresses (already formatted correctly)
        currentAddresses.addAll(newAddresses)
        
        return currentAddresses.sortedBy { it.id }
    }


    /**
     * Sets up the addresses list display.
     */
    private fun setupAddressesList(addresses: List<Address>) {
        // Clear existing address items
        addressItems.clear()
        binding.addressesContainer.removeAllViews()

        // Sort addresses by creation order (oldest first) and add each address as a display item
        addresses.sortedBy { it.id }.forEach { address ->
            addAddressDisplayItem(address)
        }
    }

    /**
     * Shows the add address dialog.
     */
    private fun showAddAddressDialog() {
        // Check if person is saved or if we're in draft mode
        if (currentPerson?.id?.isNotEmpty() == true) {
            // Person is saved, use normal flow
            openAddressPicker("Dirección")
        } else {
            // Person not saved yet, use draft mode
            openAddressPickerDraft("Dirección")
        }
    }

    /**
     * Opens the AddressPickerActivity for draft mode (person not saved yet).
     */
    private fun openAddressPickerDraft(label: String) {
        val intent = Intent(this, AddressPickerActivity::class.java)
        intent.putExtra(AddressPickerActivity.EXTRA_ADDRESS_LABEL, label)
        intent.putExtra(AddressPickerActivity.EXTRA_DRAFT_MODE, true)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 1003) // Different request code for draft mode
    }

    /**
     * Opens the AddressPickerActivity.
     */
    private fun openAddressPicker(label: String) {
        currentPerson?.let { person ->
            if (person.id.isEmpty()) {
                CustomToast.showError(this, "Debe guardar la persona primero antes de agregar direcciones.")
                return
            }

            customLoader.show()
            val intent = Intent(this, AddressPickerActivity::class.java)
            intent.putExtra(AddressPickerActivity.EXTRA_PERSON_ID, person.id)
            intent.putExtra(AddressPickerActivity.EXTRA_ADDRESS_LABEL, label)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 1001)
        } ?: run {
            CustomToast.showError(this, "Debe guardar la persona primero antes de agregar direcciones.")
        }
    }

    /**
     * Handles the result from AddressPickerActivity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Adding new address
            @Suppress("DEPRECATION")
            val address = data?.getParcelableExtra(AddressPickerActivity.RESULT_ADDRESS) as? Address
            address?.let {
                saveAddress(it)
            } ?: run {
                customLoader.hide()
                CustomToast.showError(this, "Error al obtener la nueva dirección.")
            }
        } else if (requestCode == 1002 && resultCode == RESULT_OK) {
            // Editing existing address
            @Suppress("DEPRECATION")
            val address = data?.getParcelableExtra(AddressPickerActivity.RESULT_ADDRESS) as? Address
            address?.let {
                updateAddress(it)
            } ?: run {
                customLoader.hide()
                CustomToast.showError(this, "Error al obtener la dirección editada.")
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            // Adding new address in draft mode
            @Suppress("DEPRECATION")
            val address = data?.getParcelableExtra(AddressPickerActivity.RESULT_ADDRESS) as? Address
            address?.let {
                addDraftAddress(it)
            } ?: run {
                CustomToast.showError(this, "Error al obtener la nueva dirección.")
            }
        } else if (requestCode == 1001 || requestCode == 1002 || requestCode == 1003) {
            // AddressPicker was canceled
            customLoader.hide()
            CustomToast.showWarning(this, "Operación cancelada.")
        }
    }

    /**
     * Adds a new address to the draft list (not saved to database yet).
     */
    private fun saveAddress(address: Address) {
        customLoader.hide()
        
        // Add to new addresses list
        newAddresses.add(address)
        
        // Refresh the display
        setupAddressesList(getCurrentAddresses())
        
        CustomToast.showSuccess(this, "Dirección agregada (se guardará al confirmar).")
    }

    /**
     * Adds a new address to the draft list for new persons (not saved to database yet).
     */
    private fun addDraftAddress(address: Address) {
        // Add to new addresses list
        newAddresses.add(address)
        
        // Refresh the display
        setupAddressesList(getCurrentAddresses())
        
        CustomToast.showSuccess(this, "Dirección agregada (se guardará al confirmar).")
    }

    /**
     * Adds an address display item to the addresses container.
     */
    private fun addAddressDisplayItem(address: Address) {
        val addressDisplayView = layoutInflater.inflate(com.estaciondulce.app.R.layout.item_address_display, binding.addressesContainer, false)
        
        val addressLabelText = addressDisplayView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.addressLabelText)
        val addressText = addressDisplayView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.addressText)
        val addressDetailText = addressDisplayView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.addressDetailText)
        val editLabelButton = addressDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.editLabelButton)
        val editButton = addressDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.editAddressButton)
        val deleteButton = addressDisplayView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.deleteAddressButton)
        
        // Set address data
        addressLabelText.text = address.label
        addressText.text = address.formattedAddress
        
        // Set address detail
        if (address.detail.isNotEmpty()) {
            addressDetailText.text = address.detail
            addressDetailText.visibility = View.VISIBLE
        } else {
            addressDetailText.visibility = View.GONE
        }
        
        // Set up edit label button
        editLabelButton.setOnClickListener {
            editAddressLabel(address)
        }
        
        // Set up edit button (now opens map picker)
        editButton.setOnClickListener {
            editAddressLocation(address)
        }
        
        // Set up delete button
        deleteButton.setOnClickListener {
            deleteAddress(address)
        }
        
        // Add to container at the end and track it
        binding.addressesContainer.addView(addressDisplayView)
        addressItems.add(addressDisplayView)
    }

    /**
     * Edits an existing address label.
     */
    private fun editAddressLabel(address: Address) {
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_address, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val addressLabelInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.addressLabelInput)
        val addressDetailInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.estaciondulce.app.R.id.addressDetailInput)
        val confirmButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.confirmButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.cancelButton)

        // Pre-fill current values
        addressLabelInput.setText(address.label)
        addressDetailInput.setText(address.detail)
        dialog.setTitle("Editar Dirección")

        // Set up buttons
        confirmButton.setOnClickListener {
            val newLabel = addressLabelInput.text.toString().trim()
            val newDetail = addressDetailInput.text.toString().trim()
            
            if (newLabel.isEmpty()) {
                CustomToast.showError(this, "Ingrese una etiqueta para la dirección.")
                return@setOnClickListener
            }

            if (newDetail.length > 128) {
                CustomToast.showError(this, "Los detalles no pueden superar los 128 caracteres.")
                return@setOnClickListener
            }

            val updatedAddress = address.copy(label = newLabel, detail = newDetail)
            updateAddress(updatedAddress)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Edits an existing address location by opening the map picker.
     */
    private fun editAddressLocation(address: Address) {
        currentPerson?.let { person ->
            customLoader.show()
            val intent = Intent(this, AddressPickerActivity::class.java)
            intent.putExtra(AddressPickerActivity.EXTRA_PERSON_ID, person.id)
            intent.putExtra(AddressPickerActivity.EXTRA_ADDRESS_LABEL, address.label)
            intent.putExtra(AddressPickerActivity.EXTRA_EDIT_MODE, true)
            intent.putExtra(AddressPickerActivity.EXTRA_ADDRESS_TO_EDIT, address)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 1002) // Different request code for editing
        }
    }

    /**
     * Updates an existing address.
     */
    private fun updateAddress(address: Address) {
        customLoader.hide()
        
        // Check if this is a new address or an existing one
        val isNewAddress = newAddresses.any { it.id == address.id }
        
        if (isNewAddress) {
            // Update in new addresses list
            val index = newAddresses.indexOfFirst { it.id == address.id }
            if (index != -1) {
                newAddresses[index] = address
            }
        } else {
            // Update in edited addresses list
            val existingEditedIndex = editedAddresses.indexOfFirst { it.id == address.id }
            if (existingEditedIndex != -1) {
                editedAddresses[existingEditedIndex] = address
            } else {
                editedAddresses.add(address)
            }
        }
        
        // Refresh the display
        setupAddressesList(getCurrentAddresses())
        
        CustomToast.showSuccess(this, "Dirección actualizada (se guardará al confirmar).")
    }

    /**
     * Marks an address for deletion (not deleted from database until save).
     */
    private fun deleteAddress(address: Address) {
        DeleteConfirmationDialog.show(
            context = this,
            itemName = "${address.label}: ${address.formattedAddress}",
            itemType = "dirección",
            onConfirm = {
                // Check if this is a new address (not yet saved)
                val isNewAddress = newAddresses.any { it.id == address.id }
                
                if (isNewAddress) {
                    // Remove from new addresses list
                    newAddresses.removeAll { it.id == address.id }
                } else {
                    // Mark as deleted
                    deletedAddressIds.add(address.id)
                    // Remove from edited addresses if it was there
                    editedAddresses.removeAll { it.id == address.id }
                }
                
                // Refresh the display
                setupAddressesList(getCurrentAddresses())
                
                CustomToast.showSuccess(this, "Dirección eliminada (se confirmará al guardar).")
            }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Valida que los campos obligatorios estén completos y cumplan las restricciones.
     */
    private fun validateInputs(): Boolean {
        val name = binding.personNameInput.text.toString().trim()
        if (name.isEmpty()) {
            CustomToast.showError(this, "El nombre es obligatorio.")
            return false
        }

        val lastName = binding.personLastNameInput.text.toString().trim()
        if (lastName.isEmpty()) {
            CustomToast.showError(this, "El apellido es obligatorio.")
            return false
        }

        // Validate phones
        if (phoneItems.isEmpty()) {
            CustomToast.showError(this, "Debe agregar al menos un teléfono.")
            return false
        }

        if (!isUniquePerson(name, lastName, currentPerson?.id)) {
            CustomToast.showError(this, "La persona ya existe.")
            return false
        }
        return true
    }

    /**
     * Comprueba que no exista otra persona con el mismo nombre y apellido (ignorando mayúsculas/minúsculas).
     * Se utiliza la LiveData global de personas del repositorio.
     */
    private fun isUniquePerson(name: String, lastName: String, excludingId: String? = null): Boolean {
        return FirestoreRepository.personsLiveData.value?.none { person ->
            person.name.equals(name, ignoreCase = true) &&
                    person.lastName.equals(lastName, ignoreCase = true) &&
                    person.id != excludingId
        } ?: true
    }

    /**
     * Extrae un objeto Person de los valores ingresados en los campos.
     */
    private fun getPersonFromInputs(): Person {
        val name = binding.personNameInput.text.toString().trim()
        val lastName = binding.personLastNameInput.text.toString().trim()
        val typeText = binding.personTypeSpinner.text.toString()
        val type = EPersonType.getDbValue(typeText)

        // Collect all phones from display items
        val phones = mutableListOf<Phone>()
        for (i in 0 until binding.phonesContainer.childCount) {
            val phoneView = binding.phonesContainer.getChildAt(i)
            val phoneNumberText = phoneView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.phoneNumberText)
            val phoneText = phoneNumberText.text.toString()
            
            // Parse phone text "351 1234567" -> prefix="351", suffix="1234567"
            val parts = phoneText.split(" ", limit = 2)
            if (parts.size == 2) {
                phones.add(Phone(
                    phoneNumberPrefix = parts[0],
                    phoneNumberSuffix = parts[1]
                ))
            }
        }

        return Person(
            id = currentPerson?.id ?: "",
            name = name,
            lastName = lastName,
            phones = phones,
            type = type,
            addresses = currentPerson?.addresses ?: listOf()
        )
    }



    /**
     * Guarda la persona (agrega una nueva o actualiza la existente) usando PersonsHelper.
     */
    private fun savePerson() {
        if (!validateInputs()) return

        customLoader.show()

        val personToSave = getPersonFromInputs()

        if (currentPerson == null) {
            personsHelper.addPerson(
                person = personToSave,
                onSuccess = { savedPerson ->
                    // Update currentPerson with the saved person data (including the new ID)
                    currentPerson = savedPerson
                    
                    // Save all addresses after person is created
                    saveAllAddresses {
                        customLoader.hide()
                        CustomToast.showSuccess(this, "Persona y direcciones guardadas correctamente.")
                        
                        // Show tabs now that person has an ID
                        initializeTabVisibility()
                        
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al añadir la persona: ${exception.message}")
                }
            )
        } else {
            personsHelper.updatePerson(
                personId = currentPerson!!.id,
                person = personToSave,
                onSuccess = {
                    // Save all addresses after person is updated
                    saveAllAddresses {
                        customLoader.hide()
                        CustomToast.showSuccess(this, "Persona y direcciones actualizadas correctamente.")
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al actualizar la persona: ${exception.message}")
                }
            )
        }
    }

    /**
     * Saves all address changes (new, edited, deleted) to the database.
     */
    private fun saveAllAddresses(onComplete: () -> Unit) {
        val personId = currentPerson?.id ?: return
        
        var operationsCompleted = 0
        val totalOperations = newAddresses.size + editedAddresses.size + deletedAddressIds.size
        
        if (totalOperations == 0) {
            onComplete()
            return
        }
        
        fun checkIfAllOperationsCompleted() {
            operationsCompleted++
            if (operationsCompleted >= totalOperations) {
                onComplete()
            }
        }
        
        // Save new addresses
        newAddresses.forEach { address ->
            addressesHelper.addAddressToPerson(
                personId = personId,
                address = address,
                onSuccess = { checkIfAllOperationsCompleted() },
                onError = { exception ->
                    CustomToast.showError(this, "Error al guardar nueva dirección: ${exception.message}")
                    checkIfAllOperationsCompleted()
                }
            )
        }
        
        // Update edited addresses
        editedAddresses.forEach { address ->
            addressesHelper.updateAddressInPerson(
                personId = personId,
                address = address,
                onSuccess = { checkIfAllOperationsCompleted() },
                onError = { exception ->
                    CustomToast.showError(this, "Error al actualizar dirección: ${exception.message}")
                    checkIfAllOperationsCompleted()
                }
            )
        }
        
        // Delete marked addresses
        deletedAddressIds.forEach { addressId ->
            addressesHelper.deleteAddressFromPerson(
                personId = personId,
                addressId = addressId,
                onSuccess = { checkIfAllOperationsCompleted() },
                onError = { exception ->
                    CustomToast.showError(this, "Error al eliminar dirección: ${exception.message}")
                    checkIfAllOperationsCompleted()
                }
            )
        }
    }
}
