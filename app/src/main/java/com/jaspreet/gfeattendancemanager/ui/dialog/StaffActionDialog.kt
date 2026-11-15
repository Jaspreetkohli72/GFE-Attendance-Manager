package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.databinding.DialogStaffActionBinding
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class StaffActionDialog : DialogFragment() {

    private var _binding: DialogStaffActionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel
    private var inactivityJob: Job? = null

    private var staffId: String = ""
    private var staffName: String = ""
    private var clockedIn: Boolean = false
    private var since: String? = null

    companion object {
        private const val ARG_STAFF_ID = "arg_staff_id"
        private const val ARG_STAFF_NAME = "arg_staff_name"
        private const val ARG_CLOCKED_IN = "arg_clocked_in"
        private const val ARG_SINCE = "arg_since"

        fun newInstance(staff: StaffMember): StaffActionDialog {
            val dialog = StaffActionDialog()
            val args = Bundle().apply {
                putString(ARG_STAFF_ID, staff.id)
                putString(ARG_STAFF_NAME, staff.name)
                putBoolean(ARG_CLOCKED_IN, staff.clockedIn)
                putString(ARG_SINCE, staff.since)
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            staffId = it.getString(ARG_STAFF_ID, "")
            staffName = it.getString(ARG_STAFF_NAME, "")
            clockedIn = it.getBoolean(ARG_CLOCKED_IN, false)
            since = it.getString(ARG_SINCE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStaffActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]

        KioskLogger.sys("StaffActionDialog opened for $staffName ($staffId)")

        viewModel.pausePolling()

        binding.tvWorkerName.text = staffName

        if (clockedIn) {
            val statusText = if (!since.isNullOrBlank()) {
                getString(R.string.clocked_in_since, since)
            } else {
                getString(R.string.clocked_in)
            }
            binding.tvWorkerStatus.text = statusText
            binding.tvWorkerStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_clock_in_text))

            binding.btnPrimaryAction.text = getString(R.string.clock_out)
            binding.btnPrimaryAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_clock_out_btn))
        } else {
            binding.tvWorkerStatus.text = getString(R.string.currently_clocked_out)
            binding.tvWorkerStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))

            binding.btnPrimaryAction.text = getString(R.string.clock_in)
            binding.btnPrimaryAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.color_clock_in_stroke))
        }

        setupListeners()
        resetInactivityTimer()
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

    private fun setupListeners() {
        val touchResetListener = View.OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            resetInactivityTimer()
            false
        }

        binding.cardContent.setOnTouchListener(touchResetListener)
        binding.root.setOnTouchListener(touchResetListener)
        binding.btnPrimaryAction.setOnTouchListener(touchResetListener)
        binding.btnAddAdvance.setOnTouchListener(touchResetListener)
        binding.btnCancel.setOnTouchListener(touchResetListener)

        binding.btnPrimaryAction.setOnClickListener {
            resetInactivityTimer()
            val action = if (clockedIn) "OUT" else "IN"
            KioskLogger.user("[USER ACTION] StaffActionDialog: Tapped CLOCK $action for $staffName ($staffId)")
            viewModel.punch(staffId, action)
            dismiss()
        }

        binding.btnAddAdvance.setOnClickListener {
            resetInactivityTimer()
            KioskLogger.user("[USER ACTION] StaffActionDialog: Tapped ADD ADVANCE for $staffName ($staffId)")
            val advanceDialog = AdvanceDialog.newInstance(staffId, staffName) {
                dismiss()
            }
            advanceDialog.show(parentFragmentManager, "AdvanceDialog")
        }

        binding.btnCancel.setOnClickListener {
            KioskLogger.user("[USER ACTION] StaffActionDialog: Tapped Cancel for $staffName ($staffId)")
            dismiss()
        }
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = lifecycleScope.launch {
            delay(10.seconds)
            if (isAdded && _binding != null) {
                KioskLogger.sys("StaffActionDialog: Inactivity timeout, auto-dismissing")
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        inactivityJob?.cancel()
        inactivityJob = null
        KioskLogger.sys("StaffActionDialog dismissed for $staffName ($staffId)")
        viewModel.resumePolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        inactivityJob?.cancel()
        inactivityJob = null
        _binding = null
    }
}
