package com.jaspreet.gfeattendancemanager.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jaspreet.gfeattendancemanager.R
import com.jaspreet.gfeattendancemanager.databinding.DialogSyncLogBinding
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.LogEntry
import com.jaspreet.gfeattendancemanager.util.LogType
import kotlinx.coroutines.launch

class SyncLogDialog : DialogFragment() {

    private var _binding: DialogSyncLogBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(): SyncLogDialog {
            return SyncLogDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSyncLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        KioskLogger.sys("SyncLogDialog opened")

        renderExistingLogs()
        observeLogStream()

        binding.btnClearLogs.setOnClickListener {
            binding.tvLogTerminal.text = ""
        }

        binding.btnCloseLogs.setOnClickListener {
            dismiss()
        }
    }

    private fun renderExistingLogs() {
        val builder = SpannableStringBuilder()
        val logs = KioskLogger.getLogs()
        for (log in logs) {
            appendLogSpan(builder, log)
        }
        binding.tvLogTerminal.text = builder
        scrollToBottom()
    }

    private fun observeLogStream() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                KioskLogger.logFlow.collect { entry ->
                    val builder = SpannableStringBuilder(binding.tvLogTerminal.text)
                    appendLogSpan(builder, entry)
                    binding.tvLogTerminal.text = builder
                    scrollToBottom()
                }
            }
        }
    }

    private fun appendLogSpan(builder: SpannableStringBuilder, entry: LogEntry) {
        if (builder.isNotEmpty()) {
            builder.append("\n")
        }
        val start = builder.length
        val logLine = "[${entry.timestamp}] [${entry.type.name}] ${entry.message}"
        builder.append(logLine)

        val colorRes = when (entry.type) {
            LogType.USER -> R.color.color_clock
            LogType.API -> R.color.color_clock_in_text
            LogType.SYS -> R.color.text_secondary
            LogType.WARN -> R.color.color_advance
            LogType.ERR -> R.color.color_terminated_text
        }
        val colorInt = ContextCompat.getColor(requireContext(), colorRes)
        builder.setSpan(ForegroundColorSpan(colorInt), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun scrollToBottom() {
        binding.svLogContainer.post {
            binding.svLogContainer.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt().coerceAtLeast(480),
                (resources.displayMetrics.heightPixels * 0.80).toInt().coerceAtLeast(400)
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
