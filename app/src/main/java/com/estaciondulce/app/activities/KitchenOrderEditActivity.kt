package com.estaciondulce.app.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.estaciondulce.app.R
import com.estaciondulce.app.adapters.ImageCarouselAdapter
import com.estaciondulce.app.adapters.ImageViewOnlyAdapter
import com.estaciondulce.app.databinding.DialogSingleImageBinding
import com.estaciondulce.app.databinding.ActivityKitchenOrderEditBinding
import com.estaciondulce.app.helpers.KitchenOrdersHelper
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.enums.EKitchenOrderItemStatus
import com.estaciondulce.app.models.parcelables.KitchenOrder
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to manage kitchen order statuses for a specific movement
 */
class KitchenOrderEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKitchenOrderEditBinding
    private var movement: Movement? = null
    private var kitchenOrders: List<KitchenOrder> = emptyList()
    private val kitchenOrdersHelper = KitchenOrdersHelper()
    private lateinit var customLoader: CustomLoader
    private lateinit var referenceImageAdapter: ImageViewOnlyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKitchenOrderEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        customLoader = CustomLoader(this)
        setupToolbar()
        setupReferenceImagesAdapter()
        loadMovementData()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalles del Pedido"
    }

    private fun setupReferenceImagesAdapter() {
        referenceImageAdapter = ImageViewOnlyAdapter { imageUrl ->
            showImageFullScreen(imageUrl)
        }
        binding.referenceImagesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.referenceImagesRecyclerView.adapter = referenceImageAdapter
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

        if (movement == null) {
            CustomToast.showError(this, "Movimiento no encontrado")
            finish()
            return
        }

        displayMovementData()
        loadKitchenOrders(movementId)
    }

    private fun displayMovementData() {
        val movement = this.movement ?: return

        val person = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
        binding.clientNameValue.text = if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"

        binding.dateValue.text = formatDateToSpanish(movement.delivery?.date ?: movement.movementDate)
        
        val deliveryTypeText = if (movement.delivery?.type == EDeliveryType.SHIPMENT.name) {
            "Envío"
        } else {
            "Retira en local"
        }
        binding.deliveryTypeValue.text = deliveryTypeText
        
        binding.totalValue.text = "$${String.format("%.2f", movement.totalAmount)}"
        
        if (movement.detail.isNotEmpty()) {
            binding.detailValue.text = movement.detail
            binding.detailCard.visibility = View.VISIBLE
        } else {
            binding.detailCard.visibility = View.GONE
        }

        if (movement.referenceImages.isNotEmpty()) {
            referenceImageAdapter.updateImages(movement.referenceImages)
            binding.referenceImagesCard.visibility = View.VISIBLE
        } else {
            binding.referenceImagesCard.visibility = View.GONE
        }
    }

    private fun loadKitchenOrders(movementId: String) {
        kitchenOrdersHelper.getKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = { orders ->
                kitchenOrders = orders
                displayKitchenOrders()
            },
            onError = { exception ->
                CustomToast.showError(this, "Error al cargar pedidos: ${exception.message}")
            }
        )
    }

    private fun displayKitchenOrders() {
        val container = binding.kitchenOrdersList
        container.removeAllViews()

        if (kitchenOrders.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No hay items en este pedido"
                textSize = 16f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 32, 0, 32)
            }
            container.addView(emptyText)
            return
        }

        kitchenOrders.forEach { kitchenOrder ->
            val itemView = createKitchenOrderItemView(kitchenOrder)
            container.addView(itemView)
        }

        checkAndShowMarkAsDeliveredButton()
    }

    private fun checkAndShowMarkAsDeliveredButton() {
        val movement = this.movement ?: return
        
        val hasNoShipment = movement.delivery?.type != EDeliveryType.SHIPMENT.name
        val allItemsReady = kitchenOrders.isNotEmpty() && kitchenOrders.all { it.status == EKitchenOrderItemStatus.READY }
        val notAlreadyDone = movement.kitchenOrderStatus != EKitchenOrderStatus.DONE
        
        if (hasNoShipment && allItemsReady && notAlreadyDone) {
            binding.markAsDeliveredButton.visibility = View.VISIBLE
            binding.markAsDeliveredButton.setOnClickListener {
                markOrderAsDelivered()
            }
        } else {
            binding.markAsDeliveredButton.visibility = View.GONE
        }
    }

    private fun markOrderAsDelivered() {
        val movement = this.movement ?: return
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_delivery_confirmation, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
            executeMarkAsDelivered(movement)
        }
        
        dialog.show()
    }
    
    private fun executeMarkAsDelivered(movement: Movement) {
        customLoader.show()
        
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
        
        val movementData = mapOf(
            "kitchenOrderStatus" to EKitchenOrderStatus.DONE.name
        )
        
        val genericHelper = com.estaciondulce.app.helpers.GenericHelper()
        genericHelper.updateDocument(
            collectionName = "movements",
            documentId = movement.id,
            data = movementData,
            onSuccess = {
                customLoader.hide()
                CustomToast.showSuccess(this, "Pedido marcado como entregado")
                
                this.movement = movement.copy(kitchenOrderStatus = EKitchenOrderStatus.DONE)
                loadKitchenOrders(movement.id)
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(this, "Error al marcar como entregado: ${exception.message}")
            }
        )
    }

    private fun createKitchenOrderItemView(kitchenOrder: KitchenOrder): View {
        val inflater = layoutInflater
        val itemView = inflater.inflate(R.layout.item_kitchen_order, null)

        val itemName = itemView.findViewById<TextView>(R.id.itemName)
        val itemStatus = itemView.findViewById<TextView>(R.id.itemStatus)
        val statusActionButton = itemView.findViewById<MaterialButton>(R.id.statusActionButton)

        val quantity = if (kitchenOrder.quantity == kitchenOrder.quantity.toInt().toDouble()) {
            kitchenOrder.quantity.toInt().toString()
        } else {
            String.format("%.1f", kitchenOrder.quantity)
        }
        itemName.text = "${quantity}x ${kitchenOrder.name}"
        
        val statusTextValue = getStatusText(kitchenOrder.status)
        val statusColor = getStatusColor(kitchenOrder.status)
        itemStatus.text = statusTextValue
        itemStatus.setTextColor(statusColor)
        
        if (kitchenOrder.status == EKitchenOrderItemStatus.READY || kitchenOrder.status == EKitchenOrderItemStatus.DONE) {
            statusActionButton.visibility = View.GONE
        } else {
            statusActionButton.visibility = View.VISIBLE
            statusActionButton.setOnClickListener {
                showStatusUpdateDialog(kitchenOrder)
            }
        }

        return itemView
    }

    private fun showStatusUpdateDialog(kitchenOrder: KitchenOrder) {
        val currentStatus = kitchenOrder.status
        val nextStatuses = getNextPossibleStatuses(currentStatus)

        if (nextStatuses.isEmpty()) {
            CustomToast.showInfo(this, "No se puede cambiar el estado desde ${getStatusText(currentStatus)}")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_kitchen_order_status, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        setupDialogContent(dialogView, dialog, kitchenOrder, nextStatuses)
        dialog.show()
    }

    private fun setupDialogContent(
        dialogView: View,
        dialog: androidx.appcompat.app.AlertDialog,
        kitchenOrder: KitchenOrder,
        nextStatuses: List<EKitchenOrderItemStatus>
    ) {
        val subtitleText = dialogView.findViewById<TextView>(R.id.subtitleText)
        val singleActionButton = dialogView.findViewById<MaterialButton>(R.id.singleActionButton)
        val twoActionButtonsContainer = dialogView.findViewById<LinearLayout>(R.id.twoActionButtonsContainer)
        val readyButton = dialogView.findViewById<MaterialButton>(R.id.readyButton)

        when (nextStatuses.size) {
            1 -> {
                val nextStatus = nextStatuses[0]
                subtitleText.text = "El item pasará a: ${getStatusText(nextStatus).uppercase()}"
                singleActionButton.visibility = View.VISIBLE
                twoActionButtonsContainer.visibility = View.GONE
                
                singleActionButton.setOnClickListener {
                    updateKitchenOrderStatus(kitchenOrder, nextStatus)
                    dialog.dismiss()
                }
            }
            2 -> {
                subtitleText.text = "Selecciona el nuevo estado:"
                singleActionButton.visibility = View.GONE
                twoActionButtonsContainer.visibility = View.VISIBLE
                
                readyButton.setOnClickListener {
                    updateKitchenOrderStatus(kitchenOrder, EKitchenOrderItemStatus.READY)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun updateKitchenOrderStatus(kitchenOrder: KitchenOrder, newStatus: EKitchenOrderItemStatus) {
        val movement = this.movement ?: return

        customLoader.show()

        kitchenOrdersHelper.updateKitchenOrderStatus(
            movementId = movement.id,
            kitchenOrderId = kitchenOrder.id,
            newStatus = newStatus,
            onSuccess = {
                customLoader.hide()
                CustomToast.showSuccess(this, "Estado actualizado correctamente")
                loadKitchenOrders(movement.id)
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(this, "Error al actualizar estado: ${exception.message}")
            }
        )
    }

    private fun getNextPossibleStatuses(currentStatus: EKitchenOrderItemStatus): List<EKitchenOrderItemStatus> {
        return when (currentStatus) {
            EKitchenOrderItemStatus.PENDING -> listOf(EKitchenOrderItemStatus.READY_TO_DECORATE)
            EKitchenOrderItemStatus.READY_TO_DECORATE -> listOf(EKitchenOrderItemStatus.READY)
            EKitchenOrderItemStatus.READY -> emptyList()
            EKitchenOrderItemStatus.DONE -> emptyList()
        }
    }

    private fun getStatusText(status: EKitchenOrderItemStatus): String {
        return when (status) {
            EKitchenOrderItemStatus.PENDING -> "Pendiente"
            EKitchenOrderItemStatus.READY_TO_DECORATE -> "Listo para decorar"
            EKitchenOrderItemStatus.READY -> "Listo"
            EKitchenOrderItemStatus.DONE -> "Entregado"
        }
    }

    private fun getStatusColor(status: EKitchenOrderItemStatus): Int {
        return when (status) {
            EKitchenOrderItemStatus.PENDING -> Color.GRAY
            EKitchenOrderItemStatus.READY_TO_DECORATE -> Color.BLUE
            EKitchenOrderItemStatus.READY -> Color.GREEN
            EKitchenOrderItemStatus.DONE -> Color.DKGRAY
        }
    }

    private fun formatDateToSpanish(date: Date): String {
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

    private fun showImageFullScreen(imageUrl: String) {
        val movement = this.movement ?: return
        val images = movement.referenceImages
        
        
        val intent = Intent(this, FullScreenImageActivity::class.java)
        intent.putExtra("imageUrl", imageUrl)
        intent.putStringArrayListExtra("images", ArrayList(images))
        startActivity(intent)
    }
    
    private fun updateImageCounter(counterView: TextView, current: Int, total: Int) {
        counterView.text = "$current / $total"
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
