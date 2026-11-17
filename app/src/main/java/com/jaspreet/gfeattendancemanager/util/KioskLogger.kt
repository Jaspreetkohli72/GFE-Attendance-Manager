package com.jaspreet.gfeattendancemanager.util

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogType { USER, API, SYS, WARN, ERR }

data class LogEntry(
    val timestamp: String,
    val type: LogType,
    val message: String
)

object KioskLogger {
    private const val MAX_LOG_HISTORY = 100
    private val logHistory = ConcurrentLinkedQueue<LogEntry>()
    private val threadLocalTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }

    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 50)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    fun getLogs(): List<LogEntry> = logHistory.toList()

    private fun addLog(type: LogType, msg: String) {
        val timeFormatter = threadLocalTimeFormat.get() ?: SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = timeFormatter.format(Date())
        val entry = LogEntry(timeStr, type, msg)
        logHistory.add(entry)
        while (logHistory.size > MAX_LOG_HISTORY) {
            logHistory.poll()
        }
        _logFlow.tryEmit(entry)
    }

    fun user(msg: String) {
        Log.i("KioskUser", "[USER] $msg")
        addLog(LogType.USER, msg)
    }

    fun api(msg: String) {
        Log.d("KioskApi", "[API] $msg")
        addLog(LogType.API, msg)
    }

    fun sys(msg: String) {
        Log.v("KioskSys", "[SYS] $msg")
        addLog(LogType.SYS, msg)
    }

    fun warn(msg: String) {
        Log.w("KioskWarn", "[WARN] $msg")
        addLog(LogType.WARN, msg)
    }

    fun error(msg: String, throwable: Throwable? = null) {
        if (throwable != null && isNetworkTimeoutOrUnknownHost(throwable)) {
            val formattedMsg = "$msg: ${throwable.javaClass.simpleName} (${throwable.localizedMessage ?: throwable.message ?: "Network error"})"
            warn(formattedMsg)
        } else if (throwable != null) {
            Log.e("KioskErr", "[ERR] $msg", throwable)
            addLog(LogType.ERR, "$msg: ${throwable.message}")
        } else {
            Log.e("KioskErr", "[ERR] $msg")
            addLog(LogType.ERR, msg)
        }
    }

    private fun isNetworkTimeoutOrUnknownHost(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is java.net.SocketTimeoutException ||
                cause is java.net.UnknownHostException ||
                cause is java.io.InterruptedIOException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
