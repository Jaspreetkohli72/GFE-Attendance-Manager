package com.jaspreet.gfeattendancemanager.util

import android.util.Log

object KioskLogger {
    fun user(msg: String) {
        Log.i("KioskUser", "[USER] $msg")
    }

    fun api(msg: String) {
        Log.d("KioskApi", "[API] $msg")
    }

    fun sys(msg: String) {
        Log.v("KioskSys", "[SYS] $msg")
    }

    fun warn(msg: String) {
        Log.w("KioskWarn", "[WARN] $msg")
    }

    fun error(msg: String, throwable: Throwable? = null) {
        if (throwable != null && isNetworkTimeoutOrUnknownHost(throwable)) {
            warn("$msg: ${throwable.javaClass.simpleName} (${throwable.localizedMessage ?: throwable.message ?: "Network error"})")
        } else if (throwable != null) {
            Log.e("KioskErr", "[ERR] $msg", throwable)
        } else {
            Log.e("KioskErr", "[ERR] $msg")
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
