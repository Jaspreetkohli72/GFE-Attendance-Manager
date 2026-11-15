package com.jaspreet.gfeattendancemanager.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.databinding.DialogAdvanceBinding
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger

class AdvanceDialog : DialogFragment() {

    private var _binding: DialogAdvanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel

    private var staffId: String = ""
    private var staffName: String = ""
    private var onSuccessCallback: (() -> Unit)? = null

    companion object {
        private const val ARG_STAFF_ID = "arg_staff_id"
        private const val ARG_STAFF_NAME = "arg_staff_name"

        fun newInstance(
            staffId: String,
            staffName: String,
            onSuccess: (() -> Unit)? = null
        ): AdvanceDialog {
            val dialog = AdvanceDialog()
            val args = Bundle().apply {
                putString(ARG_STAFF_ID, staffId)
                putString(ARG_STAFF_NAME, staffName)
            }
            dialog.arguments = args
            dialog.onSuccessCallback = onSuccess
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            staffId = it.getString(ARG_STAFF_ID, "")
            staffName = it.getString(ARG_STAFF_NAME, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdvanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]

        KioskLogger.sys("AdvanceDialog opened for $staffName ($staffId)")

        binding.tvTitle.text = getString(R.string.record_cash_advance_title, staffName)

        setupPresetButtons()
        setupActionButtons()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.70).toInt().coerceAtLeast(400),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun setupPresetButtons() {
        binding.btn100.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdvanceDialog: Preset amount ₹100 selected")
            binding.etAmount.setText(getString(R.string.amount_100))
        }
        binding.btn200.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdvanceDialog: Preset amount ₹200 selected")
            binding.etAmount.setText(getString(R.string.amount_200))
        }
        binding.btn500.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdvanceDialog: Preset amount ₹500 selected")
            binding.etAmount.setText(getString(R.string.amount_500))
        }
        binding.btn1000.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdvanceDialog: Preset amount ₹1000 selected")
            binding.etAmount.setText(getString(R.string.amount_1000))
        }
    }

    private fun setupActionButtons() {
        binding.btnConfirmAdvance.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim() ?: ""
            val amount = amountText.toDoubleOrNull()

            if (amount == null || amount <= 0.0) {
                KioskLogger.warn("AdvanceDialog: Invalid amount entered ($amountText)")
                Toast.makeText(requireContext(), getString(R.string.please_enter_valid_amount), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val checkedId = binding.rgGivenBy.checkedRadioButtonId
            val selectedRadioButton = if (checkedId != View.NO_ID) {
                binding.rgGivenBy.findViewById<RadioButton>(checkedId)
            } else null
            val selectedGivenBy = selectedRadioButton?.text?.toString()?.trim()
            val notes = if (!selectedGivenBy.isNullOrBlank()) selectedGivenBy else "Sandeep"

            KioskLogger.user("[USER ACTION] AdvanceDialog: Confirmed Advance ₹$amountText for $staffName (Given by $notes)")
            viewModel.addAdvance(staffId, amount, notes)
            dismiss()
            onSuccessCallback?.invoke()
        }

        binding.btnCancel.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdvanceDialog: Tapped Cancel")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        KioskLogger.sys("AdvanceDialog dismissed for $staffName ($staffId)")
        _binding = null
    }
}
