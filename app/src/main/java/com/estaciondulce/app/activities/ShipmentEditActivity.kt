package com.estaciondulce.app.activities

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ActivityShipmentEditBinding
import com.estaciondulce.app.models.EShipmentStatus
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.MovementItem
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to display shipment details in read-only mode.
 */
class ShipmentEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShipmentEditBinding
    private var movement: Movement? = null
    private val addressesHelper = AddressesHelper()
    private lateinit var customLoader: CustomLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShipmentEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customLoader = CustomLoader(this)
        setupToolbar()
        loadMovementData()
        setupStatusButton()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalles del Envío"
    }

    private fun loadMovementData() {
        val movementId = intent.getStringExtra("movementId")
        if (movementId == null) {
            CustomToast.showError(this, "ID de movimiento no válido")
            finish()
            return
        }

        // Find the movement from the repository
        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        movement = movements.find { it.id == movementId }

        if (movement == null || movement?.shipment == null) {
            CustomToast.showError(this, "Envío no encontrado")
            finish()
            return
        }

        displayShipmentData()
        setupStatusButton()
    }

    private fun displayShipmentData() {
        val movement = this.movement ?: return
        val shipment = movement.shipment ?: return

        // Date
        binding.dateValue.text = formatDateToSpanish(shipment.date ?: Date())

        // Person name
        val person = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
        binding.personValue.text = if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"

        // Address
        binding.addressValue.text = shipment.formattedAddress
        
        // Address detail
        if (shipment.addressId.isNotEmpty()) {
            // Try to get address detail from person's addresses
            val personForAddress = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
            personForAddress?.let { p ->
                // We need to load addresses to get the detail
                loadAddressDetail(p.id, shipment.addressId)
            }
        }

        // Items
        binding.itemsValue.text = formatItemsList(movement.items)
        
        // Detail
        if (movement.detail.isNotEmpty()) {
            binding.detailValue.text = "Detalle: ${movement.detail}"
            binding.detailValue.visibility = android.view.View.VISIBLE
        } else {
            binding.detailValue.visibility = android.view.View.GONE
        }

        // Cost
        binding.costValue.text = "$${String.format("%.2f", shipment.cost)}"
        
        // Total
        binding.totalValue.text = "$${String.format("%.2f", movement.totalAmount)}"

        // Phone
        val personPhone = person?.phones?.firstOrNull()
        if (personPhone != null) {
            val displayPhoneNumber = "${personPhone.phoneNumberPrefix} ${personPhone.phoneNumberSuffix}"
            val callPhoneNumber = "${personPhone.phoneNumberPrefix}${personPhone.phoneNumberSuffix}"
            binding.phoneValue.text = displayPhoneNumber
            binding.callButton.setOnClickListener {
                callPhoneNumber(callPhoneNumber)
            }
            binding.phoneValue.visibility = View.VISIBLE
            binding.callButton.visibility = View.VISIBLE
        } else {
            binding.phoneValue.visibility = View.GONE
            binding.callButton.visibility = View.GONE
        }

        // Status with color
        val statusText = getStatusText(shipment.status)
        binding.statusValue.text = statusText
        binding.statusValue.setTextColor(getStatusColor(shipment.status))

        // Google Maps button
        binding.googleMapsButton.setOnClickListener {
            openGoogleMaps(shipment.lat, shipment.lng)
        }
    }

    private fun openGoogleMaps(lat: Double, lng: Double) {
        try {
            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            // Fallback to web version
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=$lat,$lng"))
            startActivity(webIntent)
        }
    }

    private fun getStatusText(status: EShipmentStatus): String {
        return when (status) {
            EShipmentStatus.PENDING -> "Pendiente"
            EShipmentStatus.IN_PROGRESS -> "En Progreso"
            EShipmentStatus.DELIVERED -> "Entregado"
            EShipmentStatus.CANCELED -> "Cancelado"
        }
    }

    private fun getStatusColor(status: EShipmentStatus): Int {
        return when (status) {
            EShipmentStatus.PENDING -> Color.GRAY
            EShipmentStatus.IN_PROGRESS -> Color.BLUE
            EShipmentStatus.DELIVERED -> Color.GREEN
            EShipmentStatus.CANCELED -> Color.RED
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

    private fun loadAddressDetail(personId: String, addressId: String) {
        addressesHelper.getAddressesForPerson(
            personId = personId,
            onSuccess = { addresses ->
                val address = addresses.find { it.id == addressId }
                if (address != null && address.detail.isNotEmpty()) {
                    binding.addressDetailValue.text = address.detail
                    binding.addressDetailValue.visibility = android.view.View.VISIBLE
                } else {
                    binding.addressDetailValue.visibility = android.view.View.GONE
                }
            },
            onError = { exception ->
                android.util.Log.e("ShipmentEditActivity", "Error loading address detail: ${exception.message}")
                binding.addressDetailValue.visibility = android.view.View.GONE
            }
        )
    }

    private fun formatItemsList(items: List<MovementItem>): String {
        val regularItems = items.filter { it.collection != "custom" || it.collectionId != "discount" }
        
        return if (regularItems.isEmpty()) {
            "Sin items"
        } else {
            regularItems.joinToString("\n") { item ->
                val name = getItemName(item)
                val quantity = if (item.quantity == item.quantity.toInt().toDouble()) {
                    item.quantity.toInt().toString()
                } else {
                    String.format("%.1f", item.quantity)
                }
                "$quantity x $name"
            }
        }
    }

    private fun getItemName(item: MovementItem): String {
        return when (item.collection) {
            "products" -> {
                val product = FirestoreRepository.productsLiveData.value?.find { it.id == item.collectionId }
                product?.name ?: "Producto desconocido"
            }
            "recipes" -> {
                val recipe = FirestoreRepository.recipesLiveData.value?.find { it.id == item.collectionId }
                recipe?.name ?: "Receta desconocida"
            }
            "custom" -> {
                item.customName ?: "Item personalizado"
            }
            else -> "Item desconocido"
        }
    }

    private fun callPhoneNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            CustomToast.showError(this, "No hay aplicación de teléfono disponible.")
        }
    }

    private fun setupStatusButton() {
        binding.statusActionButton.setOnClickListener {
            movement?.let { movement ->
                showStatusChangeDialog(movement)
            }
        }
        
        // Hide button if status is DELIVERED or CANCELED
        movement?.shipment?.status?.let { status ->
            if (status == EShipmentStatus.DELIVERED || status == EShipmentStatus.CANCELED) {
                binding.statusActionButton.visibility = View.GONE
            }
        }
    }

    private fun showStatusChangeDialog(movement: Movement) {
        val currentStatus = movement.shipment?.status
        
        when (currentStatus) {
            EShipmentStatus.PENDING -> {
                showPendingToInProgressDialog(movement)
            }
            EShipmentStatus.IN_PROGRESS -> {
                showInProgressOptionsDialog(movement)
            }
            EShipmentStatus.DELIVERED, EShipmentStatus.CANCELED -> {
                CustomToast.showInfo(this, "Este envío ya está finalizado")
                return
            }
            null -> {
                CustomToast.showError(this, "Estado de envío no válido")
                return
            }
        }
    }

    private fun showPendingToInProgressDialog(movement: Movement) {
        if (movement.kitchenOrderStatus != com.estaciondulce.app.models.EKitchenOrderStatus.READY) {
            CustomToast.showError(this, "No se puede iniciar el envío. El pedido debe estar listo en cocina.")
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_shipment_status, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        setupDialogContent(dialogView, dialog, movement, listOf(EShipmentStatus.IN_PROGRESS))
        dialog.show()
    }
    
    private fun showInProgressOptionsDialog(movement: Movement) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_shipment_status, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        setupDialogContent(dialogView, dialog, movement, listOf(EShipmentStatus.DELIVERED, EShipmentStatus.CANCELED))
        dialog.show()
    }

    private fun setupDialogContent(
        dialogView: View,
        dialog: androidx.appcompat.app.AlertDialog,
        movement: Movement,
        nextStatuses: List<EShipmentStatus>
    ) {
        val subtitleText = dialogView.findViewById<TextView>(R.id.subtitleText)
        val singleActionButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.singleActionButton)
        val twoActionButtonsContainer = dialogView.findViewById<LinearLayout>(R.id.twoActionButtonsContainer)
        val deliveredButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deliveredButton)
        val canceledButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.canceledButton)

        when (nextStatuses.size) {
            1 -> {
                val nextStatus = nextStatuses[0]
                subtitleText.text = "El envío pasará a: ${getStatusText(nextStatus).uppercase()}"
                singleActionButton.visibility = View.VISIBLE
                twoActionButtonsContainer.visibility = View.GONE
                
                singleActionButton.setOnClickListener {
                    updateShipmentStatus(movement, nextStatus)
                    dialog.dismiss()
                }
            }
            2 -> {
                subtitleText.text = "Selecciona el nuevo estado:"
                singleActionButton.visibility = View.GONE
                twoActionButtonsContainer.visibility = View.VISIBLE
                
                deliveredButton.setOnClickListener {
                    updateShipmentStatus(movement, EShipmentStatus.DELIVERED)
                    dialog.dismiss()
                }
                
                canceledButton.setOnClickListener {
                    updateShipmentStatus(movement, EShipmentStatus.CANCELED)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun updateShipmentStatus(movement: Movement, newStatus: EShipmentStatus) {
        val updatedShipment = movement.shipment?.copy(status = newStatus)
        
        val updatedKitchenOrderStatus = if (newStatus == EShipmentStatus.DELIVERED) {
            com.estaciondulce.app.models.EKitchenOrderStatus.DONE
        } else {
            movement.kitchenOrderStatus
        }
        
        val updatedMovement = movement.copy(
            shipment = updatedShipment,
            kitchenOrderStatus = updatedKitchenOrderStatus
        )
        
        val movementsHelper = MovementsHelper()
        
        customLoader.show()
        
        movementsHelper.updateMovement(
            movementId = movement.id,
            movement = updatedMovement,
            updateKitchenOrders = false,
            onSuccess = {
                customLoader.hide()
                val message = if (newStatus == EShipmentStatus.DELIVERED) {
                    "Envío marcado como entregado y pedido completado"
                } else {
                    "Estado actualizado correctamente"
                }
                CustomToast.showSuccess(this, message)
                
                // Update local movement and refresh UI
                this.movement = updatedMovement
                loadMovementData()
                setupStatusButton()
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(this, "Error al actualizar estado: ${exception.message}")
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
}
