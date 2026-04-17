package com.example.sdk_qa.core

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sdk_qa.R

class LogEntryAdapter : RecyclerView.Adapter<LogEntryAdapter.VH>() {

    private val entries = mutableListOf<LogEntry>()

    /** Máximo de entradas en memoria para evitar crecimiento ilimitado */
    private val maxEntries = 100

    fun addEntry(entry: LogEntry) {
        entries.add(0, entry)
        if (entries.size > maxEntries) entries.removeAt(entries.lastIndex)
        notifyItemInserted(0)
    }

    fun clear() {
        val size = entries.size
        entries.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(entries[position])
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTimestamp: TextView = view.findViewById(R.id.tv_timestamp)
        private val tvEvent: TextView = view.findViewById(R.id.tv_event)
        private val tvDetail: TextView = view.findViewById(R.id.tv_detail)
        private val viewDot: View = view.findViewById(R.id.view_category_dot)

        fun bind(entry: LogEntry) {
            tvTimestamp.text = entry.timestamp
            tvEvent.text = entry.event
            tvEvent.setTextColor(entry.category.color)
            viewDot.background.setTint(entry.category.color)

            if (entry.detail != null) {
                tvDetail.text = entry.detail
                tvDetail.visibility = View.VISIBLE
            } else {
                tvDetail.visibility = View.GONE
            }
        }
    }
}
