package com.jaspreet.gfeattendancemanager.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.databinding.DialogEditStaffBinding
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger

class EditStaffDialog : DialogFragment() {

    private var _binding: DialogEditStaffBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel

    private var staffId: String = ""
    private var staffName: String = ""
    private var staffRole: String = ""
    private var staffSalary: Double = 0.0
    private var staffPhone: String = ""
    private var staffStatus: String = "Available"

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_ROLE = "arg_role"
        private const val ARG_SALARY = "arg_salary"
        private const val ARG_PHONE = "arg_phone"
        private const val ARG_STATUS = "arg_status"

        val DEFAULT_ROLES = arrayOf(
            "Welder", "Helper", "Fitter", "Fabricator",
            "Supervisor", "Operator", "Painter", "Electrician", "Driver", "Manager"
        )

        fun newInstance(staff: StaffMember): EditStaffDialog {
            val dialog = EditStaffDialog()
            val args = Bundle().apply {
                putString(ARG_ID, staff.id)
                putString(ARG_NAME, staff.name)
                putString(ARG_ROLE, staff.role ?: "Helper")
                putDouble(ARG_SALARY, staff.salary ?: 0.0)
                putString(ARG_PHONE, staff.phone ?: "")
                putString(ARG_STATUS, staff.status ?: "Available")
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            staffId = it.getString(ARG_ID, "")
            staffName = it.getString(ARG_NAME, "")
            staffRole = it.getString(ARG_ROLE, "Helper")
            staffSalary = it.getDouble(ARG_SALARY, 0.0)
            staffPhone = it.getString(ARG_PHONE, "")
            staffStatus = it.getString(ARG_STATUS, "Available")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditStaffBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]

        KioskLogger.sys("EditStaffDialog opened for $staffName ($staffId)")

        binding.tvEditStaffTitle.text = getString(R.string.edit_staff_title, staffName)
        binding.etEditStaffName.setText(staffName)

        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, DEFAULT_ROLES)
        binding.etEditStaffRole.setAdapter(roleAdapter)
        binding.etEditStaffRole.setText(staffRole, false)

        val salaryStr = if (staffSalary % 1.0 == 0.0) staffSalary.toInt().toString() else staffSalary.toString()
        binding.etEditStaffSalary.setText(salaryStr)
        binding.etEditStaffPhone.setText(staffPhone)

        when {
            staffStatus.startsWith("Terminated", ignoreCase = true) -> binding.rbEditTerminated.isChecked = true
            staffStatus.equals("On Leave", ignoreCase = true) -> binding.rbEditOnLeave.isChecked = true
            else -> binding.rbEditAvailable.isChecked = true
        }

        binding.btnSubmitEditStaff.setOnClickListener {
            val newName = binding.etEditStaffName.text?.toString()?.trim() ?: staffName
            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a valid staff name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRole = binding.etEditStaffRole.text?.toString()?.trim()?.ifBlank { "Helper" } ?: "Helper"
            val newSalary = binding.etEditStaffSalary.text?.toString()?.trim()?.toDoubleOrNull() ?: staffSalary
            val newPhone = binding.etEditStaffPhone.text?.toString()?.trim() ?: staffPhone

            val newStatus = when {
                binding.rbEditTerminated.isChecked -> "Terminated"
                binding.rbEditOnLeave.isChecked -> "On Leave"
                else -> "Available"
            }

            KioskLogger.user("[USER ACTION] EditStaffDialog: Updating staff $staffId -> $newName ($newRole, ₹$newSalary, $newPhone, $newStatus)")
            viewModel.updateStaff(staffId, newName, newRole, newSalary, newPhone, newStatus) { isSuccess, _ ->
                if (isSuccess) {
                    dismiss()
                }
            }
        }

        binding.btnCancelEditStaff.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels * 0.70).toInt().coerceAtLeast(380)
            val maxHeight = (metrics.heightPixels * 0.90).toInt()
            setLayout(width, maxHeight)
            setBackgroundDrawableResource(android.R.color.transparent)

            WindowCompat.setDecorFitsSystemWindows(this, false)
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
