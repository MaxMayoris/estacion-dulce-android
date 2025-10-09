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
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.enums.EShipmentStatus
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.MovementItem
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.enums.EKitchenOrderItemStatus
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.ConfirmationDialog
import com.estaciondulce.app.helpers.AddressesHelper
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.helpers.KitchenOrdersHelper
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

        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        movement = movements.find { it.id == movementId }

        if (movement == null || movement?.delivery?.type != EDeliveryType.SHIPMENT.name) {
            CustomToast.showError(this, "Envío no encontrado")
            finish()
            return
        }

        displayShipmentData()
        setupStatusButton()
    }

    private fun displayShipmentData() {
        val movement = this.movement ?: return
        val delivery = movement.delivery ?: return

        binding.dateValue.text = formatDateToSpanish(delivery.date)

        val person = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
        binding.personValue.text = if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"

        binding.addressValue.text = delivery.shipment?.formattedAddress ?: ""
        
        if (delivery.shipment?.addressId?.isNotEmpty() == true) {
            val personForAddress = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
            personForAddress?.let { p ->
                loadAddressDetail(p.id, delivery.shipment.addressId)
            }
        }

        binding.itemsValue.text = formatItemsList(movement.items)
        
        if (movement.detail.isNotEmpty()) {
            binding.detailValue.text = "Detalle: ${movement.detail}"
            binding.detailValue.visibility = android.view.View.VISIBLE
        } else {
            binding.detailValue.visibility = android.view.View.GONE
        }

        binding.costValue.text = "$${String.format("%.2f", delivery.shipment?.cost ?: 0.0)}"
        
        binding.totalValue.text = "$${String.format("%.2f", movement.totalAmount)}"

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

        val statusText = getStatusText(delivery.status)
        binding.statusValue.text = statusText
        binding.statusValue.setTextColor(getStatusColor(delivery.status))

        binding.googleMapsButton.setOnClickListener {
            openGoogleMaps(delivery.shipment?.lat ?: 0.0, delivery.shipment?.lng ?: 0.0)
        }
    }

    private fun openGoogleMaps(lat: Double, lng: Double) {
        try {
            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=$lat,$lng"))
            startActivity(webIntent)
        }
    }

    private fun getStatusText(status: String?): String {
        return when (status) {
            "PENDING" -> "Pendiente"
            "IN_PROGRESS" -> "En Progreso"
            "DELIVERED" -> "Entregado"
            "CANCELED" -> "Cancelado"
            null -> "Pendiente"
            else -> "Pendiente"
        }
    }

    private fun getStatusColor(status: String?): Int {
        return when (status) {
            "PENDING" -> Color.GRAY
            "IN_PROGRESS" -> Color.BLUE
            "DELIVERED" -> Color.GREEN
            "CANCELED" -> Color.RED
            null -> Color.GRAY
            else -> Color.GRAY
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
            onError = { _ ->
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
        
        movement?.delivery?.status?.let { status ->
            if (status == "DELIVERED" || status == "CANCELED") {
                binding.statusActionButton.visibility = View.GONE
            }
        }
    }

    private fun showStatusChangeDialog(movement: Movement) {
        val currentStatus = movement.delivery?.status
        
        when (currentStatus) {
            "PENDING" -> {
                showPendingToInProgressDialog(movement)
            }
            "IN_PROGRESS" -> {
                showInProgressOptionsDialog(movement)
            }
            "DELIVERED", "CANCELED" -> {
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
        if (movement.kitchenOrderStatus != EKitchenOrderStatus.READY) {
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
                subtitleText.text = "El envío pasará a: ${getStatusText(nextStatus.name).uppercase()}"
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
                    dialog.dismiss()
                    showCancelShipmentConfirmation(movement)
                }
            }
        }
    }

    /**
     * Shows confirmation dialog before canceling a shipment
     */
    private fun showCancelShipmentConfirmation(movement: Movement) {
        val person = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
        val clientName = person?.let { "${it.name} ${it.lastName}" } ?: "el cliente"
        
        ConfirmationDialog.show(
            context = this,
            title = "Cancelar envío",
            message = "¿Estás seguro de que querés cancelar el envío para $clientName?",
            confirmButtonText = "Sí, cancelar",
            cancelButtonText = "No, volver",
            onConfirm = {
                updateShipmentStatus(movement, EShipmentStatus.CANCELED)
            }
        )
    }

    private fun updateShipmentStatus(movement: Movement, newStatus: EShipmentStatus) {
        val updatedDelivery = movement.delivery?.copy(status = newStatus.name)
        
        val updatedKitchenOrderStatus = if (newStatus == EShipmentStatus.DELIVERED) {
            EKitchenOrderStatus.DONE
        } else {
            movement.kitchenOrderStatus
        }
        
        val updatedMovement = movement.copy(
            delivery = updatedDelivery,
            kitchenOrderStatus = updatedKitchenOrderStatus
        )
        
        val movementsHelper = MovementsHelper()
        val kitchenOrdersHelper = KitchenOrdersHelper()
        
        customLoader.show()
        
        if (newStatus == EShipmentStatus.DELIVERED) {
            kitchenOrdersHelper.getKitchenOrdersForMovement(
                movementId = movement.id,
                onSuccess = { kitchenOrders ->
                    kitchenOrders.forEach { kitchenOrder ->
                        kitchenOrdersHelper.updateKitchenOrderStatus(
                            movementId = movement.id,
                            kitchenOrderId = kitchenOrder.id,
                            newStatus = EKitchenOrderItemStatus.DONE,
                            onSuccess = { },
                            onError = { exception ->
                                customLoader.hide()
                                CustomToast.showError(this, "Error al actualizar kitchen order: ${exception.message}")
                            }
                        )
                    }
                    
                    movementsHelper.updateMovement(
                        movementId = movement.id,
                        movement = updatedMovement,
                        updateKitchenOrders = false,
                        onSuccess = {
                            customLoader.hide()
                            CustomToast.showSuccess(this, "Envío marcado como entregado y pedido completado")
                            
                            this.movement = updatedMovement
                            displayShipmentData()
                            setupStatusButton()
                        },
                        onError = { exception ->
                            customLoader.hide()
                            CustomToast.showError(this, "Error al actualizar movimiento: ${exception.message}")
                        }
                    )
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al obtener kitchen orders: ${exception.message}")
                }
            )
        } else {
            movementsHelper.updateMovement(
                movementId = movement.id,
                movement = updatedMovement,
                updateKitchenOrders = false,
                onSuccess = {
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Estado actualizado correctamente")
                    
                    this.movement = updatedMovement
                    displayShipmentData()
                    setupStatusButton()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al actualizar estado: ${exception.message}")
                }
            )
        }
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
