package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private var isSubmitting = false

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
            if (isSubmitting) return@setOnClickListener

            val name = binding.etStaffName.text?.toString()?.trim() ?: ""
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a valid staff name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val role = binding.etStaffRole.text?.toString()?.trim()?.ifBlank { "Helper" } ?: "Helper"
            val salaryStr = binding.etStaffSalary.text?.toString()?.trim()
            val salary = salaryStr?.toDoubleOrNull()

            if (salary == null || salary < 0.0 || salary > 100000.0) {
                Toast.makeText(requireContext(), "Daily wage must be between ₹0 and ₹100,000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val phone = binding.etStaffPhone.text?.toString()?.trim() ?: ""
            val status = if (binding.rbOnLeave.isChecked) "On Leave" else "Available"

            isSubmitting = true
            binding.pbSubmitCreateStaff.visibility = View.VISIBLE
            binding.btnSubmitCreateStaff.text = ""
            binding.btnSubmitCreateStaff.isEnabled = false
            binding.btnCancelCreateStaff.isEnabled = false

            KioskLogger.user("[USER ACTION] CreateStaffDialog: Creating staff $name ($role, ₹$salary, $phone, $status)")
            viewModel.createStaff(name, role, salary, phone, status) { isSuccess, _ ->
                if (_binding != null && isAdded) {
                    if (isSuccess) {
                        dismissAllowingStateLoss()
                    } else {
                        isSubmitting = false
                        binding.pbSubmitCreateStaff.visibility = View.GONE
                        binding.btnSubmitCreateStaff.text = getString(R.string.create_staff_button)
                        binding.btnSubmitCreateStaff.isEnabled = true
                        binding.btnCancelCreateStaff.isEnabled = true
                    }
                }
            }
        }

        binding.btnCancelCreateStaff.setOnClickListener {
            if (isSubmitting) return@setOnClickListener
            dismissAllowingStateLoss()
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? AppCompatActivity)?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
