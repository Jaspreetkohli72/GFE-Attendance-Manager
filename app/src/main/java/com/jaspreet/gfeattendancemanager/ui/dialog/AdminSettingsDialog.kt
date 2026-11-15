package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.api.ApiClient
import com.jaspreet.gfeattendancemanager.databinding.DialogSettingsBinding
import com.jaspreet.gfeattendancemanager.ui.MainActivity
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.PreferenceManager

class AdminSettingsDialog : DialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel
    private lateinit var preferenceManager: PreferenceManager

    companion object {
        private const val ADMIN_PIN = "1234"

        fun newInstance(): AdminSettingsDialog {
            return AdminSettingsDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]
        preferenceManager = PreferenceManager(requireContext())

        KioskLogger.sys("AdminSettingsDialog opened")

        setupPinGate()
        setupAdminControls()
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

    private fun setupPinGate() {
        binding.tvPinError.visibility = View.GONE
        binding.layoutPinGate.visibility = View.VISIBLE
        binding.layoutAdminControls.visibility = View.GONE

        binding.btnSubmitPin.setOnClickListener {
            val enteredPin = binding.etPin.text?.toString()?.trim() ?: ""
            KioskLogger.user("[USER ACTION] AdminSettingsDialog: Submitted PIN")
            if (enteredPin == ADMIN_PIN) {
                KioskLogger.user("[USER ACTION] AdminSettingsDialog: Correct PIN submitted")
                binding.tvPinError.visibility = View.GONE
                binding.layoutPinGate.visibility = View.GONE
                binding.layoutAdminControls.visibility = View.VISIBLE

                binding.etServerUrl.setText(preferenceManager.getBaseUrl())
            } else {
                KioskLogger.warn("AdminSettingsDialog: Incorrect PIN entered.")
                binding.tvPinError.text = getString(R.string.incorrect_pin)
                binding.tvPinError.visibility = View.VISIBLE
            }
        }

        binding.btnPinCancel.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdminSettingsDialog: Cancelled PIN entry")
            dismiss()
        }
    }

    private fun setupAdminControls() {
        binding.btnSaveUrl.setOnClickListener {
            var newUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (newUrl.isNotBlank()) {
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    newUrl = "https://$newUrl"
                }
                if (!newUrl.endsWith("/")) {
                    newUrl = "$newUrl/"
                }
                preferenceManager.setBaseUrl(newUrl)
                ApiClient.updateBaseUrl(newUrl)
                viewModel.fetchStaff()

                KioskLogger.user("[USER ACTION] Server URL updated: $newUrl")

                val mainActivity = activity as? MainActivity
                mainActivity?.showConfirmationBanner(getString(R.string.server_url_updated_successfully), isSuccess = true)
                Toast.makeText(requireContext(), getString(R.string.server_url_saved_toast, newUrl), Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                KioskLogger.warn("AdminSettingsDialog: Empty URL entered")
                Toast.makeText(requireContext(), getString(R.string.please_enter_valid_url), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSystemSettings.setOnClickListener {
            KioskLogger.user("[USER ACTION] System settings opened")
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                KioskLogger.error("Unable to open system settings", e)
                Toast.makeText(requireContext(), getString(R.string.unable_to_open_system_settings), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClose.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdminSettingsDialog: Closed admin settings")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        KioskLogger.sys("AdminSettingsDialog dismissed")
        _binding = null
    }
}
