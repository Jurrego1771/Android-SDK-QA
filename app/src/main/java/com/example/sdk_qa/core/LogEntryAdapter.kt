package com.example.sdk_qa.core

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sdk_qa.R

class LogEntryAdapter : RecyclerView.Adapter<LogEntryAdapter.VH>() {

    /** Todas las entradas registradas (newest-first). Fuente de verdad. */
    private val all = mutableListOf<LogEntry>()
    /** Subconjunto visible según el filtro de categorías. */
    private val visible = mutableListOf<LogEntry>()
    /** Categorías activas; vacío = mostrar todo. */
    private var filter: Set<LogCategory> = emptySet()

    /** Máximo de entradas en memoria para evitar crecimiento ilimitado */
    private val maxEntries = 100

    private fun passes(e: LogEntry) = filter.isEmpty() || e.category in filter

    /**
     * Recalcula la lista visible desde [all] aplicando [filter]. La lista es ≤100 y el
     * itemAnimator está desactivado, así que un notifyDataSetChanged es barato y evita la
     * contabilidad frágil de índices al filtrar.
     */
    private fun rebuildVisible() {
        visible.clear()
        all.filterTo(visible) { passes(it) }
        notifyDataSetChanged()
    }

    fun addEntry(entry: LogEntry) {
        all.add(0, entry)
        if (all.size > maxEntries) all.removeAt(all.lastIndex)
        rebuildVisible()
    }

    /** Aplica el filtro de categorías; conjunto vacío = sin filtro (mostrar todo). */
    fun setFilter(categories: Set<LogCategory>) {
        filter = categories
        rebuildVisible()
    }

    fun clear() {
        all.clear()
        rebuildVisible()
    }

    override fun getItemCount() = visible.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(visible[position])
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTimestamp: TextView = view.findViewById(R.id.tv_timestamp)
        private val tvDelta: TextView = view.findViewById(R.id.tv_delta)
        private val tvEvent: TextView = view.findViewById(R.id.tv_event)
        private val tvDetail: TextView = view.findViewById(R.id.tv_detail)
        private val viewDot: View = view.findViewById(R.id.view_category_dot)

        fun bind(entry: LogEntry) {
            tvTimestamp.text = entry.timestamp
            tvEvent.text = entry.event
            tvEvent.setTextColor(entry.category.color)
            viewDot.background.setTint(entry.category.color)

            if (entry.deltaMs >= 0) {
                tvDelta.text = formatDelta(entry.deltaMs)
                tvDelta.visibility = View.VISIBLE
            } else {
                tvDelta.visibility = View.GONE
            }

            if (entry.detail != null) {
                tvDetail.text = entry.detail
                tvDetail.visibility = View.VISIBLE
            } else {
                tvDetail.visibility = View.GONE
            }
        }

        /** ms → "+123ms" o "+1.2s" para deltas largos. */
        private fun formatDelta(ms: Long): String =
            if (ms < 1000) "+${ms}ms" else "+%.1fs".format(ms / 1000.0)
    }
}
