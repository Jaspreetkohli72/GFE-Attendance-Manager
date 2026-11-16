package com.jaspreet.gfeattendancemanager.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.databinding.ItemAdminStaffBinding

class AdminAdapter(
    private val onEditClick: (StaffMember) -> Unit
) : ListAdapter<StaffMember, AdminAdapter.AdminViewHolder>(AdminDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val binding = ItemAdminStaffBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminViewHolder(binding, onEditClick)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AdminViewHolder(
        private val binding: ItemAdminStaffBinding,
        private val onEditClick: (StaffMember) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StaffMember) {
            val context = binding.root.context
            binding.tvAdminStaffName.text = item.name

            val roleStr = item.role?.takeIf { it.isNotBlank() } ?: "Helper"
            val salaryVal = item.salary?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "0"
            binding.tvAdminStaffRoleSalary.text = "$roleStr • ₹$salaryVal/day"

            when {
                item.isTerminated -> {
                    binding.tvAdminStaffStatus.setBackgroundResource(R.drawable.bg_badge_terminated)
                    binding.tvAdminStaffStatus.setTextColor(ContextCompat.getColor(context, R.color.color_terminated_text))
                    binding.tvAdminStaffStatus.text = context.getString(R.string.badge_terminated)
                }
                item.isOnLeave -> {
                    binding.tvAdminStaffStatus.setBackgroundResource(R.drawable.bg_badge_on_leave)
                    binding.tvAdminStaffStatus.setTextColor(ContextCompat.getColor(context, R.color.color_on_leave_text))
                    binding.tvAdminStaffStatus.text = context.getString(R.string.badge_on_leave)
                }
                item.isActuallyClockedIn -> {
                    binding.tvAdminStaffStatus.setBackgroundResource(R.drawable.bg_badge_clocked_in)
                    binding.tvAdminStaffStatus.setTextColor(ContextCompat.getColor(context, R.color.color_clock_in_text))
                    val statusText = if (!item.since.isNullOrBlank()) {
                        context.getString(R.string.status_in_since, item.since)
                    } else {
                        context.getString(R.string.status_in)
                    }
                    binding.tvAdminStaffStatus.text = statusText
                }
                else -> {
                    binding.tvAdminStaffStatus.setBackgroundResource(R.drawable.bg_badge_clocked_out)
                    binding.tvAdminStaffStatus.setTextColor(ContextCompat.getColor(context, R.color.color_clock_out_text))
                    binding.tvAdminStaffStatus.text = context.getString(R.string.badge_available)
                }
            }

            binding.root.setOnClickListener {
                onEditClick(item)
            }
        }
    }

    private class AdminDiffCallback : DiffUtil.ItemCallback<StaffMember>() {
        override fun areItemsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StaffMember, newItem: StaffMember): Boolean {
            return oldItem == newItem
        }
    }
}
