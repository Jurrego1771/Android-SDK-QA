package com.example.sdk_qa.core

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sdk_qa.R

class ScenarioAdapter(
    private val items: List<ScenarioListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SCENARIO = 1
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ScenarioListItem.Header -> TYPE_HEADER
        is ScenarioListItem.Scenario -> TYPE_SCENARIO
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(
                inflater.inflate(R.layout.item_scenario_header, parent, false)
            )
            else -> ScenarioVH(
                inflater.inflate(R.layout.item_scenario, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ScenarioListItem.Header -> (holder as HeaderVH).bind(item)
            is ScenarioListItem.Scenario -> (holder as ScenarioVH).bind(item)
        }
    }

    // ---- ViewHolders ----

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: ScenarioListItem.Header) {
            (itemView as TextView).text = item.title.uppercase()
        }
    }

    class ScenarioVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tv_title)
        private val tvDescription: TextView = view.findViewById(R.id.tv_description)
        private val tvStatus: TextView = view.findViewById(R.id.tv_status)

        fun bind(item: ScenarioListItem.Scenario) {
            tvTitle.text = item.title
            tvDescription.text = item.description
            tvStatus.text = item.status.emoji

            itemView.setOnClickListener {
                val ctx: Context = itemView.context
                ctx.startActivity(Intent(ctx, item.activityClass))
            }
        }
    }
}
