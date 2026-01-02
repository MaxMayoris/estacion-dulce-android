package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.repository.FirestoreRepository
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying calendar days in a grid (2 weeks = 14 days)
 */
class CalendarDayAdapter(
    private val days: List<CalendarDayItem>,
    private val categories: Map<String, WorkCategory>,
    private var selectedDate: String,
    private val todayDate: String,
    private val onDayClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.CalendarDayViewHolder>() {

    fun updateSelectedDate(newSelectedDate: String) {
        selectedDate = newSelectedDate
        notifyDataSetChanged()
    }

    data class CalendarDayItem(
        val date: String,
        val dayNumber: Int,
        val workDay: WorkDay?
    )

    class CalendarDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayNumberText: TextView = itemView.findViewById(R.id.dayNumberText)
        val dayHoursText: TextView = itemView.findViewById(R.id.dayHoursText)
        val dayCategoriesText: TextView = itemView.findViewById(R.id.dayCategoriesText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarDayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarDayViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarDayViewHolder, position: Int) {
        val item = days[position]
        val workDay = item.workDay
        val isEmpty = item.dayNumber == 0 || item.date.isEmpty()

        if (isEmpty) {
            holder.dayNumberText.text = ""
            holder.dayHoursText.text = ""
            holder.dayCategoriesText.visibility = View.GONE
            
            val cardView = holder.itemView as com.google.android.material.card.MaterialCardView
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
            cardView.strokeWidth = 0
            holder.itemView.isClickable = false
            holder.itemView.isFocusable = false
            return
        }

        holder.dayNumberText.text = item.dayNumber.toString()

        val totalMinutes = workDay?.totalMinutes ?: 0L
        val hours = totalMinutes / 60
        val minutes = (totalMinutes % 60).toInt()
        
        val hoursText = if (hours > 0 && minutes > 0) {
            "$hours $minutes"
        } else if (hours > 0) {
            "$hours"
        } else if (minutes > 0) {
            "$minutes"
        } else {
            "0"
        }
        holder.dayHoursText.text = hoursText

        holder.dayCategoriesText.visibility = View.GONE

        val isSelected = item.date == selectedDate
        val isToday = item.date == todayDate

        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView
        val context = holder.itemView.context
        
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        
        if (isSelected) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.button_gradient_start))
            holder.dayNumberText.setTextColor(ContextCompat.getColor(context, R.color.white))
            holder.dayHoursText.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            holder.dayNumberText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.dayHoursText.setTextColor(ContextCompat.getColor(context, R.color.button_gradient_start))
        }

        if (isToday && !isSelected) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_purple))
            cardView.strokeWidth = 2
            cardView.strokeColor = ContextCompat.getColor(context, R.color.button_gradient_start)
        } else if (!isSelected) {
            cardView.strokeWidth = 0
        }

        holder.itemView.setOnClickListener {
            onDayClick(item.date)
        }
    }

    override fun getItemCount(): Int = days.size
}

