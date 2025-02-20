package com.estaciondulce.app.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.databinding.ActivityMovementEditBinding
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.adapters.MovementItemsAdapter
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

// Data class used in the add-item dialog
data class ItemEntry(val name: String, val collection: String, val collectionId: String) {
    override fun toString(): String = name
}

class MovementEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementEditBinding
    private val movementsHelper = MovementsHelper()
    private var currentMovement: Movement? = null
    private val repository = FirestoreRepository
    private var selectedDate: Date = Date()
    private var personsList: List<Person> = listOf()

    // For movement items
    private var movementItems: MutableList<MovementItem> = mutableListOf()
    private lateinit var itemsAdapter: MovementItemsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Recupera el movimiento (si se está editando)
        currentMovement = intent.getParcelableExtra("MOVEMENT")
        supportActionBar?.title = if (currentMovement != null) "Editar Movimiento" else "Agregar Movimiento"

        // Configura el campo de fecha (usando DatePickerDialog)
        binding.dateInput.setOnClickListener { showDatePickerDialog() }

        // Configura el RecyclerView para los ítems del movimiento
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemsAdapter = MovementItemsAdapter(
            movementItems,
            onItemChanged = { recalcTotalAmount() },
            onDeleteClicked = { position ->
                movementItems.removeAt(position)
                itemsAdapter.updateItems(movementItems)
                recalcTotalAmount()
            },
            // Provide a lambda to get the display name without storing it in the item.
            getDisplayName = { collection, collectionId ->
                when (collection) {
                    "products" -> repository.productsLiveData.value?.find { it.id == collectionId }?.name ?: "Desconocido"
                    "recipes" -> repository.recipesLiveData.value?.find { it.id == collectionId }?.name ?: "Desconocido"
                    else -> "Desconocido"
                }
            }
        )
        binding.itemsRecyclerView.adapter = itemsAdapter

        // Botón para agregar ítem
        binding.addItemButton.setOnClickListener { showAddItemDialog() }

        // Observa personsLiveData para poblar el spinner de personas
        repository.personsLiveData.observe(this) { persons ->
            personsList = persons
            setupPersonSpinner(persons)
            currentMovement?.let { movement ->
                val index = persons.indexOfFirst { it.id == movement.personId }
                if (index != -1) binding.personSpinner.setSelection(index)
            }
        }

        // Configura el spinner de tipo de movimiento
        setupMovementTypeSpinner()

        // Si se está editando, precarga datos: fecha, monto total, tipo, costo de envío y los ítems
        currentMovement?.let { movement ->
            selectedDate = movement.date
            binding.dateInput.setText(formatDate(movement.date))
            binding.totalAmountInput.setText(movement.totalAmount.toString())
            val movementTypeIndex = when (movement.type) {
                EMovementType.PURCHASE -> 0  // "Compra"
                EMovementType.SALE -> 1      // "Venta"
                else -> 0
            }
            binding.movementTypeSpinner.setSelection(movementTypeIndex)
            if (movement.type == EMovementType.SALE) {
                binding.shippingCostLabel.visibility = View.VISIBLE
                binding.shippingCostInput.visibility = View.VISIBLE
                val shippingCost = movement.shipment?.shippingCost ?: 0.0
                binding.shippingCostInput.setText(shippingCost.toString())
            } else {
                binding.shippingCostLabel.visibility = View.GONE
                binding.shippingCostInput.visibility = View.GONE
            }
            movementItems.clear()
            movementItems.addAll(movement.items)
            itemsAdapter.updateItems(movementItems)
            recalcTotalAmount()
        } ?: run {
            binding.dateInput.setText(formatDate(selectedDate))
        }

        // Listener para el spinner de tipo de movimiento para mostrar/ocultar costo de envío
        binding.movementTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 1) { // "Venta" seleccionada
                    binding.shippingCostLabel.visibility = View.VISIBLE
                    binding.shippingCostInput.visibility = View.VISIBLE
                } else { // "Compra" seleccionada
                    binding.shippingCostLabel.visibility = View.GONE
                    binding.shippingCostInput.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.shippingCostLabel.visibility = View.GONE
                binding.shippingCostInput.visibility = View.GONE
            }
        }

        binding.saveMovementButton.setOnClickListener { saveMovement() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Configura el spinner de personas usando personsLiveData.
     */
    private fun setupPersonSpinner(persons: List<Person>) {
        val personNames = persons.map { "${it.name} ${it.lastName}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, personNames)
        binding.personSpinner.adapter = adapter
    }

    /**
     * Configura el spinner de tipo de movimiento con las opciones "Compra" y "Venta".
     * En la BD se guardará como PURCHASE para "Compra" y SALE para "Venta".
     */
    private fun setupMovementTypeSpinner() {
        val movementTypes = listOf("Compra", "Venta")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, movementTypes)
        binding.movementTypeSpinner.adapter = adapter
    }

    /**
     * Muestra un DatePickerDialog para seleccionar la fecha.
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
     * Recalcula el monto total sumando (costo * cantidad) de cada ítem, y si es venta, añade el costo de envío.
     */
    private fun recalcTotalAmount() {
        val itemsTotal = movementItems.sumOf { it.cost * it.quantity }
        val shippingCost = if (binding.movementTypeSpinner.selectedItemPosition == 1) {
            binding.shippingCostInput.text.toString().toDoubleOrNull() ?: 0.0
        } else 0.0
        val total = itemsTotal + shippingCost
        binding.totalAmountInput.setText(total.toString())
    }

    /**
     * Muestra un diálogo de búsqueda para agregar un ítem.
     * Permite escribir para filtrar la lista combinada de productos y recetas.
     */
    private fun showAddItemDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_add_item, null)
        builder.setView(dialogView)
        val searchEditText = dialogView.findViewById<android.widget.EditText>(com.estaciondulce.app.R.id.searchEditText)
        val itemsListView = dialogView.findViewById<android.widget.ListView>(com.estaciondulce.app.R.id.itemsListView)

        // Merge products and recipes into one list.
        val products = repository.productsLiveData.value ?: emptyList()
        val recipes = repository.recipesLiveData.value ?: emptyList()
        val itemsList = mutableListOf<ItemEntry>()
        for (product in products) {
            itemsList.add(ItemEntry(product.name, "products", product.id))
        }
        for (recipe in recipes) {
            itemsList.add(ItemEntry(recipe.name, "recipes", recipe.id))
        }
        val listAdapter = ArrayAdapter<ItemEntry>(
            this,
            android.R.layout.simple_list_item_1,
            itemsList
        )
        itemsListView.adapter = listAdapter

        // Filtra la lista conforme el usuario escribe.
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
                val newItem = MovementItem(
                    collection = selectedItem.collection,
                    collectionId = selectedItem.collectionId,
                    cost = 0.0,
                    quantity = 1.0
                )
                movementItems.add(newItem)
                itemsAdapter.updateItems(movementItems)
                recalcTotalAmount()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Valida las entradas obligatorias.
     */
    private fun validateInputs(): Boolean {
        if (binding.dateInput.text.toString().trim().isEmpty()) {
            Snackbar.make(binding.root, "La fecha es obligatoria.", Snackbar.LENGTH_LONG).show()
            return false
        }
        if (binding.personSpinner.selectedItem == null) {
            Snackbar.make(binding.root, "Por favor, seleccione una persona.", Snackbar.LENGTH_LONG).show()
            return false
        }
        val totalAmountStr = binding.totalAmountInput.text.toString().trim()
        if (totalAmountStr.isEmpty()) {
            Snackbar.make(binding.root, "El monto total es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }
        if (totalAmountStr.toDoubleOrNull() == null) {
            Snackbar.make(binding.root, "El monto total debe ser un número válido.", Snackbar.LENGTH_LONG).show()
            return false
        }
        // Si el tipo seleccionado es "Venta", valida el costo de envío.
        if (binding.movementTypeSpinner.selectedItemPosition == 1) {
            val shippingCostStr = binding.shippingCostInput.text.toString().trim()
            if (shippingCostStr.isEmpty()) {
                Snackbar.make(binding.root, "El costo de envío es obligatorio para una venta.", Snackbar.LENGTH_LONG).show()
                return false
            }
            if (shippingCostStr.toDoubleOrNull() == null) {
                Snackbar.make(binding.root, "El costo de envío debe ser un número válido.", Snackbar.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    /**
     * Extrae un objeto Movement de los campos ingresados.
     * El tipo se determina según el spinner de tipo de movimiento.
     * Si "Venta" está seleccionado, se utiliza el costo de envío; si "Compra", shipment es null.
     */
    private fun getMovementFromInputs(): Movement {
        val selectedPersonIndex = binding.personSpinner.selectedItemPosition
        val selectedPerson = personsList.getOrNull(selectedPersonIndex)

        val movementType = if (binding.movementTypeSpinner.selectedItemPosition == 1) {
            EMovementType.SALE
        } else {
            EMovementType.PURCHASE
        }

        val totalAmount = binding.totalAmountInput.text.toString().trim().toDoubleOrNull() ?: 0.0

        val shipment = if (movementType == EMovementType.SALE) {
            val shippingCost = binding.shippingCostInput.text.toString().trim().toDoubleOrNull() ?: 0.0
            Shipment(
                id = "",
                addressId = "", // Se completará más adelante al integrar la dirección completa
                shippingCost = shippingCost
            )
        } else {
            null
        }

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
     * Guarda el movimiento (agrega uno nuevo o actualiza uno existente) usando MovementsHelper.
     */
    private fun saveMovement() {
        if (!validateInputs()) return

        val movementToSave = getMovementFromInputs()

        if (currentMovement == null) {
            MovementsHelper().addMovement(
                movement = movementToSave,
                onSuccess = {
                    Snackbar.make(binding.root, "Movimiento agregado correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(binding.root, "Error al agregar el movimiento: ${exception.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        } else {
            MovementsHelper().updateMovement(
                movementId = currentMovement!!.id,
                movement = movementToSave,
                onSuccess = {
                    Snackbar.make(binding.root, "Movimiento actualizado correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(binding.root, "Error al actualizar el movimiento: ${exception.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }
}
