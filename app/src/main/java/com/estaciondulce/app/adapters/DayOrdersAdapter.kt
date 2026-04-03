package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.databinding.ItemDayOrderBinding
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import java.text.SimpleDateFormat
import java.util.Locale

class DayOrdersAdapter(
    private val orders: List<Movement>,
    private val onOrderClick: (Movement) -> Unit
) : RecyclerView.Adapter<DayOrdersAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDayOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class ViewHolder(private val binding: ItemDayOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(movement: Movement) {
            val person = FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
            val clientName = person?.let { "${it.name} ${it.lastName}" } ?: "Cliente desconocido"
            binding.clientNameText.text = "Cliente: $clientName"

            val date = movement.delivery?.date ?: movement.movementDate
            val time = timeFormat.format(date)
            val isShipment = movement.delivery?.type == EDeliveryType.SHIPMENT.name
            binding.orderTimeText.text = if (isShipment) {
                "Hora de entrega: $time"
            } else {
                "Hora que retira: $time"
            }

            binding.root.setOnClickListener {
                onOrderClick(movement)
            }
        }
    }
}
