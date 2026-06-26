package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.estaciondulce.app.activities.MovementEditActivity
import com.estaciondulce.app.adapters.MovementAdapter
import com.estaciondulce.app.databinding.FragmentMovementBinding
import com.estaciondulce.app.helpers.FirebaseFunctionsHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.enums.EPersonType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.MovementItem
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.models.toColumnConfigs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import com.estaciondulce.app.adapters.PersonSearchAdapter
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager


class MovementFragment : Fragment() {

    private var _binding: FragmentMovementBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    
    // Tab state
    private var selectedTab: String = "sale" // Default to sale tab

    private var tempImageFile: File? = null
    private var selectedProviderId: String? = null
    private lateinit var customLoader: CustomLoader

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageFile?.let { file ->
                val uri = Uri.fromFile(file)
                processTicketImage(uri)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            processTicketImage(it)
        }
    }

    /**
     * Formats a date to Spanish format: "dd mes hh:mm"
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

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository.movementsLiveData.observe(viewLifecycleOwner) { _ ->
            // Apply current filter (selected tab + search)
            filterMovements(binding.searchBar.text.toString())
        }
        repository.personsLiveData.observe(viewLifecycleOwner) {
            // Apply current filter (selected tab + search)
            filterMovements(binding.searchBar.text.toString())
        }

        binding.addMovementButton.setOnClickListener {
            openMovementEditActivity(null)
        }

        binding.scanTicketButton.setOnClickListener {
            showProviderSelectionDialog()
        }

        customLoader = CustomLoader(requireContext())

        // Setup tab click listeners
        binding.saleTab.setOnClickListener {
            selectTab("sale")
        }
        
        binding.purchaseTab.setOnClickListener {
            selectTab("purchase")
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterMovements(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Launches MovementEditActivity. If a movement is passed, it is used for editing.
     */
    private fun openMovementEditActivity(movement: Movement? = null, ticketImageUri: Uri? = null) {
        val intent = Intent(requireContext(), MovementEditActivity::class.java)
        if (movement != null) {
            intent.putExtra("MOVEMENT", movement)
        }
        if (ticketImageUri != null) {
            intent.putExtra("TICKET_IMAGE_URI", ticketImageUri.toString())
        }
        movementEditActivityLauncher.launch(intent)
    }

    /**
     * Launcher for MovementEditActivity.
     */
    private val movementEditActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
        }
    }

    /**
     * Handles tab selection and updates UI accordingly.
     */
    private fun selectTab(tabType: String) {
        selectedTab = tabType
        
        // Update tab visual states
        when (tabType) {
            "sale" -> {
                binding.saleTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.saleTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.purchaseTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.purchaseTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            }
            "purchase" -> {
                binding.purchaseTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.purchaseTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.saleTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.saleTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            }
        }
        
        // Refresh table with tab filter + search
        filterMovements(binding.searchBar.text.toString())
    }

    /**
     * Configures the table with the list of movements.
     * The table displays Fecha, Nombre (obtained via personId), and Monto (Tipo column removed).
     * Movements are automatically sorted by date in descending order (newest first).
     */
    private fun setupTableView(movements: List<Movement>) {
        // Movements are already filtered by tab type in filterMovements
        val sortedList = movements.sortedByDescending { it.movementDate }
        val columnConfigs = listOf("Fecha", "Nombre", "Monto").toColumnConfigs(currencyColumns = setOf(2))
        binding.movementTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = MovementAdapter(
                movementList = sortedList,
                onRowClick = { movement -> editMovement(movement) },
                onDeleteClick = { movement -> deleteMovement(movement) }
            ) { movement ->
                val dateString = formatDateToSpanish(movement.movementDate)
                val personName =
                    repository.personsLiveData.value?.find { it.id == movement.personId }?.let {
                        "${it.name} ${it.lastName}"
                    } ?: "Desconocido"
                listOf(
                    dateString,
                    personName,
                    movement.totalAmount
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> formatDateToSpanish(movement.movementDate)
                    1 -> repository.personsLiveData.value?.find { it.id == movement.personId }
                        ?.let {
                            "${it.name} ${it.lastName}"
                        } ?: "Desconocido"
                    2 -> movement.totalAmount
                    else -> null
                }
            },
            enableColumnSorting = false
        )
    }

    /**
     * Filters the list of movements by selected tab type and name search.
     * Movements are automatically sorted by date in descending order (newest first).
     * Search is limited to the "Nombre" field only.
     */
    private fun filterMovements(searchQuery: String = "") {
        val movements = repository.movementsLiveData.value ?: emptyList()
        
        // First filter by selected tab type
        val tabFilteredMovements = when (selectedTab) {
            "sale" -> movements.filter { it.type == EMovementType.SALE }
            "purchase" -> movements.filter { it.type == EMovementType.PURCHASE }
            else -> movements
        }
        
        // Then filter by name search if provided
        val filteredList = if (searchQuery.isEmpty()) {
            tabFilteredMovements
        } else {
            tabFilteredMovements.filter { movement ->
                val personName = repository.personsLiveData.value?.find { it.id == movement.personId }
                    ?.let { "${it.name} ${it.lastName}" } ?: ""
                personName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        setupTableView(filteredList)
    }

    private fun editMovement(movement: Movement) {
        openMovementEditActivity(movement)
    }

    private fun deleteMovement(movement: Movement) {
        val movementType = if (movement.type == EMovementType.PURCHASE) "Compra" else "Venta"
        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
        val personName = person?.let { "${it.name} ${it.lastName}" } ?: "Persona desconocida"
        val formattedAmount = String.format("%.2f", movement.totalAmount)
        
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = "$movementType a $personName por $${formattedAmount}",
            itemType = "movimiento",
            onConfirm = {
                MovementsHelper().deleteMovement(
                    movementId = movement.id,
                    onSuccess = {
                        CustomToast.showSuccess(requireContext(), "$movementType a $personName eliminada correctamente.")
                    },
                    onError = {
                        CustomToast.showError(requireContext(), "Error al eliminar el movimiento.")
                    }
                )
            }
        )
    }

    private fun showImageSelectionDialog() {
        val options = listOf("Tomar Foto", "Seleccionar de Galería")
        val actions = listOf(
            { takePictureWithCamera() },
            { galleryLauncher.launch("image/*") }
        )
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Escanear Ticket con IA")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which]()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun takePictureWithCamera() {
        try {
            val tempFile = File.createTempFile("ticket_ocr_${System.currentTimeMillis()}", ".jpg", requireContext().cacheDir)
            tempImageFile = tempFile
            val uri = Uri.fromFile(tempFile)
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            CustomToast.showError(requireContext(), "Error al crear archivo temporal: ${e.message}")
        }
    }

    private fun processTicketImage(uri: Uri) {
        customLoader.show()
        lifecycleScope.launch(Dispatchers.IO) {
            val imageData = getBase64ImageFromUri(uri)
            if (imageData == null) {
                withContext(Dispatchers.Main) {
                    customLoader.hide()
                    CustomToast.showError(requireContext(), "Error al procesar la imagen del ticket.")
                }
                return@launch
            }

            val (base64Str, mimeType) = imageData
            val result = FirebaseFunctionsHelper.callProcessTicketOCR(base64Str, mimeType)

            withContext(Dispatchers.Main) {
                customLoader.hide()
                if (result != null) {
                    try {
                        val parsedMovement = mapResponseToMovement(result)
                        openMovementEditActivity(parsedMovement, uri)
                    } catch (e: Exception) {
                        Log.e("MovementFragment", "Error mapping movement response", e)
                        CustomToast.showError(requireContext(), "Error al interpretar la respuesta de la IA.")
                    }
                } else {
                    CustomToast.showError(requireContext(), "Error al extraer datos del ticket. Intenta de nuevo.")
                }
            }
        }
    }

    private fun getBase64ImageFromUri(uri: Uri): Pair<String, String>? {
        return try {
            val context = requireContext()
            // Re-use existing project ImageUtils to rotate and compress image to WebP
            val compressedUri = com.estaciondulce.app.utils.ImageUtils.compressImage(context, uri)
            val inputStream = context.contentResolver.openInputStream(compressedUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes == null) return null
            val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            // Clean up the temporary WebP file created by ImageUtils
            if (compressedUri.scheme == "file") {
                compressedUri.path?.let { File(it).delete() }
            }

            Pair(base64Str, "image/webp")
        } catch (e: Exception) {
            Log.e("MovementFragment", "Error converting image to Base64: ${e.message}", e)
            null
        }
    }

    private fun showProviderSelectionDialog() {
        val providers = repository.personsLiveData.value?.filter { 
            it.type == EPersonType.PROVIDER.dbValue 
        } ?: emptyList()
        
        if (providers.isEmpty()) {
            CustomToast.showWarning(requireContext(), "No hay proveedores registrados.")
            return
        }

        val dialogView = layoutInflater.inflate(com.estaciondulce.app.R.layout.dialog_person_search, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val searchEditText = dialogView.findViewById<android.widget.EditText>(com.estaciondulce.app.R.id.searchEditText)
        val personsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.estaciondulce.app.R.id.personsRecyclerView)
        val emptyState = dialogView.findViewById<android.widget.LinearLayout>(com.estaciondulce.app.R.id.emptyState)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.estaciondulce.app.R.id.closeButton)
        val dialogTitle = dialogView.findViewById<android.widget.TextView>(com.estaciondulce.app.R.id.dialogTitle)
        
        dialogTitle.text = "Seleccionar Proveedor"
        searchEditText.hint = "Buscar proveedor..."

        // Limit RecyclerView height to wrap_content so it takes less space
        personsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val params = personsRecyclerView.layoutParams
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        personsRecyclerView.layoutParams = params

        // Show initially up to 3 providers sorted alphabetically
        val initialProviders = providers.sortedBy { "${it.name} ${it.lastName}" }.take(3)
        
        val dialogAdapter = PersonSearchAdapter(initialProviders) { selectedPerson ->
            selectedProviderId = selectedPerson.id
            dialog.dismiss()
            showImageSelectionDialog()
        }
        
        personsRecyclerView.adapter = dialogAdapter
        
        if (initialProviders.isEmpty()) {
            personsRecyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            personsRecyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                
                val filtered = if (query.isEmpty()) {
                    providers.sortedBy { "${it.name} ${it.lastName}" }.take(3)
                } else {
                    providers.filter { 
                        "${it.name} ${it.lastName}".contains(query, ignoreCase = true) 
                    }.sortedBy { "${it.name} ${it.lastName}" }.take(3)
                }
                
                dialogAdapter.updatePersons(filtered)
                
                if (filtered.isEmpty()) {
                    personsRecyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    personsRecyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            }
        })

        dialog.show()
    }

    private fun mapResponseToMovement(data: Map<*, *>) : Movement {
        val totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0
        val rawItems = data["items"] as? List<*> ?: emptyList<Any>()
        val itemsList = mutableListOf<MovementItem>()
        
        // Parse date from response, fallback to current date
        val dateStr = data["date"] as? String
        val parsedDate = if (!dateStr.isNullOrEmpty()) {
            try {
                if (dateStr.contains(":")) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(dateStr)
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                }
            } catch (e: Exception) {
                java.util.Date()
            }
        } else {
            java.util.Date()
        }
        
        for (rawItem in rawItems) {
            val itemMap = rawItem as? Map<*, *> ?: continue
            val collection = itemMap["collection"] as? String ?: "custom"
            val collectionId = itemMap["collectionId"] as? String ?: ""
            val customName = itemMap["customName"] as? String
            val cost = (itemMap["cost"] as? Number)?.toDouble() ?: 0.0
            val quantity = (itemMap["quantity"] as? Number)?.toDouble() ?: 0.0
            
            itemsList.add(
                MovementItem(
                    collection = collection,
                    collectionId = collectionId,
                    customName = customName,
                    cost = cost,
                    quantity = quantity
                )
            )
        }
        
        return Movement(
            type = EMovementType.PURCHASE,
            personId = selectedProviderId ?: "",
            movementDate = parsedDate ?: java.util.Date(),
            totalAmount = totalAmount,
            items = itemsList,
            detail = "Ticket escaneado por IA"
        )
    }
}
