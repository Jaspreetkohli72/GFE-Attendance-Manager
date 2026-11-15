package com.jaspreet.gfeattendancemanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jaspreet.gfeattendancemanager.data.api.ApiClient
import com.jaspreet.gfeattendancemanager.data.model.AdvanceRequest
import com.jaspreet.gfeattendancemanager.data.model.GenericApiResponse
import com.jaspreet.gfeattendancemanager.data.model.PunchRequest
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.util.KioskLogger
import com.jaspreet.gfeattendancemanager.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class BannerEvent(
    val message: String,
    val isSuccess: Boolean
)

class KioskViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)
    private val gson = Gson()

    private val _staffList = MutableStateFlow<List<StaffMember>>(emptyList())
    val staffList: StateFlow<List<StaffMember>> = _staffList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _bannerEvent = MutableSharedFlow<BannerEvent>()
    val bannerEvent: SharedFlow<BannerEvent> = _bannerEvent.asSharedFlow()

    var isPollingPaused: Boolean = false
        private set

    private var pollingJob: Job? = null

    init {
        // Load initial data from PreferenceManager cache immediately
        val cachedStaff = preferenceManager.getStaffList()
        if (cachedStaff.isNotEmpty()) {
            _staffList.value = cachedStaff
            KioskLogger.sys("Loaded ${cachedStaff.size} cached staff members on initialization")
        }
        startPolling()
    }

    fun startPolling() {
        pollingJob?.cancel()
        isPollingPaused = false
        KioskLogger.sys("Background polling started")
        pollingJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                if (!isPollingPaused) {
                    fetchStaff()
                }
                delay(30.seconds)
            }
        }
    }

    fun pausePolling() {
        isPollingPaused = true
        KioskLogger.sys("Background polling paused")
    }

    fun resumePolling() {
        isPollingPaused = false
        KioskLogger.sys("Background polling resumed")
    }

    private fun parseErrorMessage(errorRaw: String?, httpCode: Int): String {
        if (errorRaw.isNullOrBlank()) {
            return "Server Error ($httpCode)"
        }
        return try {
            val parsed = gson.fromJson(errorRaw, GenericApiResponse::class.java)
            val err = parsed?.error
            val msg = parsed?.message
            when {
                !err.isNullOrBlank() -> err
                !msg.isNullOrBlank() -> msg
                else -> "Server Error ($httpCode)"
            }
        } catch (_: Exception) {
            "Server Error ($httpCode)"
        }
    }

    fun fetchStaff() {
        if (_isLoading.value) {
            return
        }
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.getStaff()

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    if (_staffList.value != list) {
                        KioskLogger.sys("Staff data updated (${list.size} members)")
                        _staffList.value = list
                        preferenceManager.saveStaffList(list)
                    }
                    _isOffline.value = false
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Fetch staff error: HTTP ${response.code()} - $errorMsg")
                    KioskLogger.warn("Fetch staff failed, falling back to cached staff data")
                    _isOffline.value = true
                    loadCachedStaff()
                }
            } catch (t: Throwable) {
                KioskLogger.error("Fetch staff network error: ${t.localizedMessage ?: "Unknown error"}", t)
                KioskLogger.warn("Fetch staff network exception, falling back to cached staff data")
                _isOffline.value = true
                loadCachedStaff()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadCachedStaff() {
        try {
            val cached = preferenceManager.getStaffList()
            if (cached.isNotEmpty()) {
                _staffList.value = cached
                KioskLogger.sys("Loaded ${cached.size} cached staff members")
            }
        } catch (t: Throwable) {
            KioskLogger.error("Failed to load cached staff", t)
        }
    }

    fun punch(
        staffId: String,
        action: String,
        onResult: ((isSuccess: Boolean, message: String, staffList: List<StaffMember>?) -> Unit)? = null
    ) {
        val currentList = _staffList.value
        val staffMember = currentList.find { it.id == staffId }
        val staffName = staffMember?.name?.takeIf { it.isNotBlank() } ?: staffId.ifBlank { "Staff" }
        val isClockIn = action.equals("IN", ignoreCase = true)
        val actionText = if (isClockIn) "IN" else "OUT"
        val currentTimeString = SimpleDateFormat("hh:mm a", Locale.US).format(Date())

        // 1. Immediately (0ms) optimistic local update handling null/empty fields safely
        val updatedList = currentList.map { member ->
            if (member.id == staffId) {
                val updatedSince = if (isClockIn) {
                    if (!member.since.isNullOrBlank() && member.clockedIn) member.since else currentTimeString
                } else {
                    null
                }
                member.copy(
                    clockedIn = isClockIn,
                    since = updatedSince
                )
            } else {
                member
            }
        }

        // Update StateFlow & PreferenceManager cache immediately
        _staffList.value = updatedList
        preferenceManager.saveStaffList(updatedList)

        // Emit success banner event immediately (0ms) so MainActivity displays banner & plays chime
        val bannerMsg = "$staffName — Clocked ${if (isClockIn) "IN!" else "OUT!"}"
        KioskLogger.user("[PUNCH OPTIMISTIC] $staffName — Clocked $actionText (0ms feedback)")
        viewModelScope.launch {
            try {
                _bannerEvent.emit(BannerEvent(bannerMsg, true))
            } catch (t: Throwable) {
                KioskLogger.error("Failed to emit punch banner event", t)
            }
        }
        try {
            onResult?.invoke(true, bannerMsg, updatedList)
        } catch (t: Throwable) {
            KioskLogger.error("Error in punch onResult callback", t)
        }

        // 2. Background Network Sync (Dispatchers.IO)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.punch(PunchRequest(staffId, action))

                if (response.isSuccessful && response.body() != null) {
                    val punchResp = response.body()!!
                    if (punchResp.success) {
                        KioskLogger.user("[PUNCH SYNC SUCCESS] $staffName — Clocked $actionText confirmed by server")
                        val returnedStaff = punchResp.staff
                        if (!returnedStaff.isNullOrEmpty()) {
                            _staffList.value = returnedStaff
                            preferenceManager.saveStaffList(returnedStaff)
                        }
                        _isOffline.value = false
                    } else {
                        val errorMsg = punchResp.message ?: "Punch rejected by server"
                        KioskLogger.error("Punch rejected for $staffName: $errorMsg")
                        fetchStaff()
                        _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Punch failed for $staffName: HTTP ${response.code()} - $errorMsg")
                    fetchStaff()
                    _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Punch network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff()
                try {
                    _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
                } catch (_: Exception) {}
            }
        }
    }

    fun addAdvance(
        staffId: String,
        amount: Double,
        notes: String = "Sandeep",
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        val staffName = _staffList.value.find { it.id == staffId }?.name?.takeIf { it.isNotBlank() } ?: staffId.ifBlank { "Staff" }
        val amountStr = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
        val bannerMsg = "₹$amountStr Advance recorded for $staffName"

        // 1. Immediately (0ms) emit success banner event & play chime
        KioskLogger.user("[ADVANCE OPTIMISTIC] ₹$amountStr Given By $notes to $staffName (0ms feedback)")
        viewModelScope.launch {
            try {
                _bannerEvent.emit(BannerEvent(bannerMsg, true))
            } catch (t: Throwable) {
                KioskLogger.error("Failed to emit advance banner event", t)
            }
        }
        try {
            onResult?.invoke(true, bannerMsg)
        } catch (t: Throwable) {
            KioskLogger.error("Error in addAdvance onResult callback", t)
        }

        // 2. Background Network Sync (Dispatchers.IO)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.addAdvance(AdvanceRequest(staffId = staffId, amount = amount, notes = notes))

                if (response.isSuccessful && response.body() != null) {
                    val advanceResp = response.body()!!
                    if (advanceResp.success) {
                        KioskLogger.user("[ADVANCE SYNC SUCCESS] ₹$amountStr Given By $notes to $staffName confirmed by server")
                        _isOffline.value = false
                        fetchStaff()
                    } else {
                        val msg = advanceResp.error ?: advanceResp.message ?: "Failed to record advance"
                        KioskLogger.error("Advance rejected for $staffName: $msg")
                        fetchStaff()
                        _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $msg", false))
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Advance failed for $staffName: HTTP ${response.code()} - $errorMsg")
                    fetchStaff()
                    _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Advance network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff()
                try {
                    _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $errorMsg", false))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
