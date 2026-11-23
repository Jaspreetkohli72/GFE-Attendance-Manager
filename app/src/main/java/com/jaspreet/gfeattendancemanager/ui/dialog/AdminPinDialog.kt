package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.databinding.DialogAdminPinBinding
import com.jaspreet.gfeattendancemanager.ui.admin.AdminActivity
import com.jaspreet.gfeattendancemanager.util.KioskLogger

class AdminPinDialog : DialogFragment() {

    private var _binding: DialogAdminPinBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ADMIN_PIN = "1234"

        fun newInstance(): AdminPinDialog {
            return AdminPinDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdminPinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        KioskLogger.sys("AdminPinDialog opened")

        binding.tvPinError.visibility = View.GONE

        binding.etPin.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_UNSPECIFIED ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                binding.btnSubmitPin.performClick()
                true
            } else {
                false
            }
        }

        binding.btnSubmitPin.setOnClickListener {
            val enteredPin = binding.etPin.text?.toString()?.trim() ?: ""
            KioskLogger.user("[USER ACTION] AdminPinDialog: Submitted PIN")

            binding.pbSubmitPin.visibility = View.VISIBLE
            binding.btnSubmitPin.text = ""
            binding.btnSubmitPin.isEnabled = false

            if (enteredPin == ADMIN_PIN) {
                KioskLogger.user("[USER ACTION] AdminPinDialog: Correct PIN submitted, opening AdminActivity")
                binding.tvPinError.visibility = View.GONE
                dismissAllowingStateLoss()
                val intent = Intent(requireContext(), AdminActivity::class.java)
                startActivity(intent)
            } else {
                KioskLogger.warn("AdminPinDialog: Incorrect PIN entered.")
                binding.pbSubmitPin.visibility = View.GONE
                binding.btnSubmitPin.text = getString(R.string.submit_pin)
                binding.btnSubmitPin.isEnabled = true
                binding.tvPinError.text = getString(R.string.incorrect_pin)
                binding.tvPinError.visibility = View.VISIBLE
            }
        }

        binding.btnPinCancel.setOnClickListener {
            KioskLogger.user("[USER ACTION] AdminPinDialog: Cancelled PIN entry")
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.65).toInt().coerceAtLeast(380),
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
