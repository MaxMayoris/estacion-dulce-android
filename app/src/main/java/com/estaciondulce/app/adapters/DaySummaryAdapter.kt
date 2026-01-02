package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkCategory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying day summaries in the timesheet fragment
 */
class DaySummaryAdapter(
    private val days: List<DaySummaryItem>,
    private val categories: Map<String, WorkCategory>,
    private val onDayClick: (String) -> Unit
) : RecyclerView.Adapter<DaySummaryAdapter.DayViewHolder>() {

    data class DaySummaryItem(
        val date: String,
        val dayName: String,
        val workDay: WorkDay?
    )

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayNameText: TextView = itemView.findViewById(R.id.dayNameText)
        val totalHoursText: TextView = itemView.findViewById(R.id.totalHoursText)
        val categoriesBreakdownText: TextView = itemView.findViewById(R.id.categoriesBreakdownText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_summary, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val item = days[position]
        val workDay = item.workDay

        holder.dayNameText.text = item.dayName

        val totalMinutes = workDay?.totalMinutes ?: 0L
        val hours = totalMinutes / 60
        val minutes = (totalMinutes % 60).toInt()
        
        if (hours > 0 && minutes > 0) {
            holder.totalHoursText.text = "${hours}h ${minutes}m"
        } else if (hours > 0) {
            holder.totalHoursText.text = "${hours}h"
        } else if (minutes > 0) {
            holder.totalHoursText.text = "${minutes}m"
        } else {
            holder.totalHoursText.text = "0h"
        }

        val breakdown = if (workDay != null && workDay.totalsByCategory.isNotEmpty()) {
            workDay.totalsByCategory.mapNotNull { (categoryId, minutes) ->
                val category = categories[categoryId]
                if (category != null && minutes > 0) {
                    val catHours = minutes / 60
                    val catMinutes = (minutes % 60).toInt()
                    val timeStr = if (catHours > 0 && catMinutes > 0) {
                        "${catHours}h ${catMinutes}m"
                    } else if (catHours > 0) {
                        "${catHours}h"
                    } else {
                        "${catMinutes}m"
                    }
                    "${category.name} $timeStr"
                } else null
            }.joinToString(", ")
        } else {
            ""
        }

        holder.categoriesBreakdownText.text = breakdown
        holder.categoriesBreakdownText.visibility = if (breakdown.isNotEmpty()) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onDayClick(item.date)
        }
    }

    override fun getItemCount(): Int = days.size
}

