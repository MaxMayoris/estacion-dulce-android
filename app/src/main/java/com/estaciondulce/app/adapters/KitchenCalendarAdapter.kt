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

    private var selectedDate: Date? = java.util.Calendar.getInstance().time

    fun setSelectedDate(date: Date?) {
        selectedDate = date
        notifyDataSetChanged()
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance()
        cal1.time = date1
        val cal2 = java.util.Calendar.getInstance()
        cal2.time = date2
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

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
            val isSelected = selectedDate != null && isSameDay(day.date, selectedDate!!)
            val isToday = isSameDay(day.date, java.util.Calendar.getInstance().time)
            
            binding.dayNumberText.text = day.dayNumber.toString()
            cardView.cardElevation = context.resources.displayMetrics.density * 1f // 1dp
            
            // Text color for orders count
            if (day.orders.isNotEmpty()) {
                binding.ordersCountText.visibility = View.VISIBLE
                binding.ordersCountText.text = "${day.orders.size}"
            } else {
                binding.ordersCountText.visibility = View.INVISIBLE
                binding.ordersCountText.text = "0"
            }
            
            binding.root.isClickable = true
            binding.root.isFocusable = true
            
            if (isSelected) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.button_gradient_start))
                binding.dayNumberText.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.ordersCountText.setTextColor(ContextCompat.getColor(context, R.color.white))
                cardView.strokeWidth = 0
            } else {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
                binding.dayNumberText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                binding.ordersCountText.setTextColor(ContextCompat.getColor(context, R.color.button_gradient_start))
            }

            if (isToday && !isSelected) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_purple))
                cardView.strokeWidth = 2
                cardView.strokeColor = ContextCompat.getColor(context, R.color.button_gradient_start)
            } else if (!isSelected) {
                cardView.strokeWidth = 0
            }

            binding.root.setOnClickListener {
                onDayClick(day)
            }
        }
    }
}
