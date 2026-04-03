package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ItemKitchenCalendarDayBinding
import com.estaciondulce.app.models.parcelables.Movement
import java.util.Date

data class CalendarDay(
    val date: Date,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val orders: List<Movement>
)

class KitchenCalendarAdapter(
    private var days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<KitchenCalendarAdapter.ViewHolder>() {

    fun updateDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKitchenCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    inner class ViewHolder(private val binding: ItemKitchenCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: CalendarDay) {
            val context = binding.root.context
            val cardView = binding.root

            // Empty cell (padding before month starts)
            if (!day.isCurrentMonth || day.dayNumber == 0) {
                binding.dayNumberText.text = ""
                binding.ordersCountText.visibility = View.INVISIBLE
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                cardView.cardElevation = 0f
                cardView.strokeWidth = 0
                binding.root.isClickable = false
                binding.root.isFocusable = false
                return
            }

            // Regular day cell
            binding.dayNumberText.text = day.dayNumber.toString()
            binding.dayNumberText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            cardView.cardElevation = context.resources.displayMetrics.density * 1f // 1dp
            cardView.strokeWidth = 0
            binding.root.isClickable = true
            binding.root.isFocusable = true

            if (day.orders.isNotEmpty()) {
                binding.ordersCountText.visibility = View.VISIBLE
                binding.ordersCountText.text = "${day.orders.size}"
                binding.ordersCountText.setTextColor(ContextCompat.getColor(context, R.color.button_gradient_start))
            } else {
                binding.ordersCountText.visibility = View.INVISIBLE
                binding.ordersCountText.text = "0"
                binding.ordersCountText.setTextColor(ContextCompat.getColor(context, R.color.button_gradient_start))
            }

            binding.root.setOnClickListener {
                if (day.orders.isNotEmpty()) {
                    onDayClick(day)
                }
            }
        }
    }
}
