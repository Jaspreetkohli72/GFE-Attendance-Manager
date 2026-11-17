package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.data.api.ApiClient
import com.jaspreet.gfeattendancemanager.databinding.DialogServerUrlBinding
import com.jaspreet.gfeattendancemanager.ui.viewmodel.KioskViewModel
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.PreferenceManager

class ServerUrlDialog : DialogFragment() {

    private var _binding: DialogServerUrlBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: KioskViewModel
    private lateinit var preferenceManager: PreferenceManager

    companion object {
        fun newInstance(): ServerUrlDialog {
            return ServerUrlDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogServerUrlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[KioskViewModel::class.java]
        preferenceManager = PreferenceManager(requireContext())

        KioskLogger.sys("ServerUrlDialog opened")

        binding.etServerUrl.setText(preferenceManager.getBaseUrl())

        binding.btnSaveUrl.setOnClickListener {
            val rawUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (rawUrl.isBlank()) {
                KioskLogger.warn("ServerUrlDialog: Empty URL entered")
                Toast.makeText(requireContext(), getString(R.string.please_enter_valid_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val validatedUrl = ApiClient.validateBaseUrl(rawUrl)
                preferenceManager.setBaseUrl(validatedUrl)
                ApiClient.updateBaseUrl(validatedUrl)
                viewModel.fetchStaff(forceRefresh = true)

                KioskLogger.user("[USER ACTION] Server URL updated: $validatedUrl")
                Toast.makeText(requireContext(), getString(R.string.server_url_saved_toast, validatedUrl), Toast.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            } catch (e: Exception) {
                KioskLogger.error("ServerUrlDialog: Malformed URL '$rawUrl'", e)
                Toast.makeText(requireContext(), "Invalid Base URL format. Please check and try again.", Toast.LENGTH_LONG).show()
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
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.70).toInt().coerceAtLeast(400),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
