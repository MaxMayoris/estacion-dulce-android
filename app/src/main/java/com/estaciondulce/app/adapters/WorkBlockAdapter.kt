package com.estaciondulce.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying work blocks in day detail
 */
class WorkBlockAdapter(
    private val blocks: List<WorkBlock>,
    private val categories: Map<String, WorkCategory>,
    private val workers: Map<String, Worker>,
    private val onEditClick: (WorkBlock) -> Unit,
    private val onDeleteClick: (WorkBlock) -> Unit
) : RecyclerView.Adapter<WorkBlockAdapter.BlockViewHolder>() {

    class BlockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeRangeText: TextView = itemView.findViewById(R.id.timeRangeText)
        val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        val workerText: TextView = itemView.findViewById(R.id.workerText)
        val noteText: TextView = itemView.findViewById(R.id.noteText)
        val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_block, parent, false)
        return BlockViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
        val block = blocks[position]

        val startTime = block.startAt?.toDate()?.let { timeFormat.format(it) } ?: ""
        val endTime = block.endAt?.toDate()?.let { timeFormat.format(it) } ?: ""
        val hours = block.durationMinutes / 60
        val minutes = (block.durationMinutes % 60).toInt()
        
        val durationStr = if (hours > 0 && minutes > 0) {
            "${hours}h ${minutes}m"
        } else if (hours > 0) {
            "${hours}h"
        } else {
            "${minutes}m"
        }

        holder.timeRangeText.text = "$startTime - $endTime ($durationStr)"

        val category = categories[block.categoryId]
        holder.categoryText.text = category?.name ?: "Desconocida"

        val worker = workers[block.workerId]
        holder.workerText.text = worker?.displayName ?: "Desconocido"

        if (block.note.isNotEmpty()) {
            holder.noteText.text = "Nota: ${block.note}"
            holder.noteText.visibility = View.VISIBLE
        } else {
            holder.noteText.visibility = View.GONE
        }

        holder.editButton.setOnClickListener {
            onEditClick(block)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(block)
        }
    }

    override fun getItemCount(): Int = blocks.size
}

