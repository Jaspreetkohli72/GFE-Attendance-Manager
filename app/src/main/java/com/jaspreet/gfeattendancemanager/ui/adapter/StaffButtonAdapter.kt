package com.jaspreet.gfeattendancemanager.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.databinding.ItemStaffButtonBinding

class StaffButtonAdapter(
    private val onStaffClick: (StaffMember) -> Unit
) : ListAdapter<StaffMember, StaffButtonAdapter.StaffViewHolder>(StaffDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val binding = ItemStaffButtonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StaffViewHolder(binding, onStaffClick)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StaffViewHolder(
        private val binding: ItemStaffButtonBinding,
        private val onStaffClick: (StaffMember) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StaffMember) {
            val context = binding.root.context
            binding.tvStaffName.text = item.name

            if (item.clockedIn) {
                // Clocked IN styling: Emerald stroke (#10B981, 2dp), dark green background (#064E3B), text #34D399 (● IN 09:15 AM)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_clocked_in)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_clock_in_text))

                val statusText = if (!item.since.isNullOrBlank()) {
                    context.getString(R.string.status_in_since, item.since)
                } else {
                    context.getString(R.string.status_in)
                }
                binding.tvStatusBadge.text = statusText
            } else {
                // Clocked OUT styling: Slate stroke (#475569, 2dp), dark slate background (#1E293B), text #94A3B8 (○ OUT)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_clocked_out)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_clock_out_text))
                binding.tvStatusBadge.text = context.getString(R.string.status_out)
            }

            binding.root.setOnClickListener {
                onStaffClick(item)
            }
        }
    }

    private class StaffDiffCallback : DiffUtil.ItemCallback<StaffMember>() {
        override fun areItemsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean {
            return oldItem == newItem
        }
    }
}
