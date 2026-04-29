package com.expediheal.autoreply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TemplateAdapter(
    private val onEdit: (Int, String) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txt: TextView = itemView.findViewById(R.id.txt_template)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEdit(pos, items[pos])
            }
            btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onDelete(pos)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_template, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.txt.text = items[position]
    }

    override fun getItemCount(): Int = items.size
}
