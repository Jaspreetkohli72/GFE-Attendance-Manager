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
import com.jaspreet.gfeattendancemanager.databinding.DialogCreateStaffBinding
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger

class CreateStaffDialog : DialogFragment() {

    private var _binding: DialogCreateStaffBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel

    companion object {
        fun newInstance(): CreateStaffDialog {
            return CreateStaffDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateStaffBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]

        KioskLogger.sys("CreateStaffDialog opened")

        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, EditStaffDialog.DEFAULT_ROLES)
        binding.etStaffRole.setAdapter(roleAdapter)

        binding.btnSubmitCreateStaff.setOnClickListener {
            val name = binding.etStaffName.text?.toString()?.trim() ?: ""
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a valid staff name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val role = binding.etStaffRole.text?.toString()?.trim()?.ifBlank { "Helper" } ?: "Helper"
            val salaryStr = binding.etStaffSalary.text?.toString()?.trim()
            val salary = salaryStr?.toDoubleOrNull() ?: 0.0
            val phone = binding.etStaffPhone.text?.toString()?.trim() ?: ""
            val status = if (binding.rbOnLeave.isChecked) "On Leave" else "Available"

            KioskLogger.user("[USER ACTION] CreateStaffDialog: Creating staff $name ($role, ₹$salary, $phone, $status)")
            viewModel.createStaff(name, role, salary, phone, status) { isSuccess, _ ->
                if (isSuccess) {
                    dismiss()
                }
            }
        }

        binding.btnCancelCreateStaff.setOnClickListener {
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
