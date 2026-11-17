package com.jaspreet.gfeattendancemanager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jaspreet.gfeattendancemanager.data.api.ApiClient
import com.jaspreet.gfeattendancemanager.data.model.AdvanceRequest
import com.jaspreet.gfeattendancemanager.data.model.CreateStaffRequest
import com.jaspreet.gfeattendancemanager.data.model.GenericApiResponse
import com.jaspreet.gfeattendancemanager.data.model.PunchRequest
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.data.model.UpdateStaffRequest
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
import kotlinx.coroutines.withContext
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

    private val _allStaffList = MutableStateFlow<List<StaffMember>>(emptyList())
    val allStaffList: StateFlow<List<StaffMember>> = _allStaffList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _bannerEvent = MutableSharedFlow<BannerEvent>()
    val bannerEvent: SharedFlow<BannerEvent> = _bannerEvent.asSharedFlow()

    private val _syncTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncTrigger: SharedFlow<Unit> = _syncTrigger.asSharedFlow()

    var isPollingPaused: Boolean = false
        private set

    private var pollingJob: Job? = null

    companion object {
        @Suppress("unused")
        private const val POLLING_INTERVAL_MS = 10_000L
    }

    init {
        // Load initial data from PreferenceManager cache immediately
        val cachedActiveStaff = preferenceManager.getActiveStaffList()
        if (cachedActiveStaff.isNotEmpty()) {
            _staffList.value = cachedActiveStaff
            KioskLogger.sys("Loaded ${cachedActiveStaff.size} cached active staff members on initialization")
        }
        val cachedAllStaff = preferenceManager.getAllStaffList()
        if (cachedAllStaff.isNotEmpty()) {
            _allStaffList.value = sortAdminStaffList(cachedAllStaff)
        }
        startPolling()
    }

    fun startPolling() {
        pollingJob?.cancel()
        isPollingPaused = false
        KioskLogger.sys("Background polling started (10s interval)")
        pollingJob = viewModelScope.launch {
            while (coroutineContext.isActive) {
                if (!isPollingPaused) {
                    fetchStaff()
                }
                delay(10.seconds)
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

    private fun sortAdminStaffList(list: List<StaffMember>): List<StaffMember> {
        return list.sortedWith(
            compareBy<StaffMember> { member ->
                when {
                    member.isAvailable -> 1
                    member.isOnLeave -> 2
                    member.isTerminated -> 3
                    else -> 4
                }
            }.thenBy { member ->
                member.name.lowercase(Locale.getDefault())
            }
        )
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
                val response = apiService.getStaff(all = false)

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    if (_staffList.value != list) {
                        KioskLogger.sys("Active staff data updated (${list.size} members)")
                        _staffList.value = list
                        preferenceManager.saveActiveStaffList(list)
                        _syncTrigger.emit(Unit)
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

    fun fetchAllStaff() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.getStaff(all = true)

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    val sortedList = sortAdminStaffList(list)
                    KioskLogger.sys("Admin: All staff data fetched (${sortedList.size} members, sorted Available -> On Leave -> Terminated)")
                    _allStaffList.value = sortedList
                    preferenceManager.saveAllStaffList(sortedList)
                    _syncTrigger.emit(Unit)
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Fetch all staff error: HTTP ${response.code()} - $errorMsg")
                    loadCachedAllStaff()
                }
            } catch (t: Throwable) {
                KioskLogger.error("Fetch all staff network error: ${t.localizedMessage ?: "Unknown error"}", t)
                loadCachedAllStaff()
            }
        }
    }

    private fun loadCachedStaff() {
        try {
            val cached = preferenceManager.getActiveStaffList()
            if (cached.isNotEmpty()) {
                _staffList.value = cached
                KioskLogger.sys("Loaded ${cached.size} cached active staff members")
            }
        } catch (t: Throwable) {
            KioskLogger.error("Failed to load cached active staff", t)
        }
    }

    private fun loadCachedAllStaff() {
        try {
            val cached = preferenceManager.getAllStaffList()
            if (cached.isNotEmpty()) {
                val sorted = sortAdminStaffList(cached)
                _allStaffList.value = sorted
                KioskLogger.sys("Loaded ${sorted.size} cached total staff members for Admin")
            }
        } catch (t: Throwable) {
            KioskLogger.error("Failed to load cached all staff", t)
        }
    }

    fun createStaff(
        name: String,
        role: String,
        salary: Double,
        phone: String,
        status: String,
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        val bannerMsg = "$name — Staff Created!"
        KioskLogger.user("[CREATE STAFF] Submitting $name ($role, ₹$salary, $status) to server...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val request = CreateStaffRequest(
                    name = name,
                    role = role,
                    salary = salary,
                    phone = phone,
                    status = status
                )
                val response = apiService.createStaff(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    KioskLogger.user("[CREATE STAFF SUCCESS] $name confirmed by server")
                    fetchAllStaff()
                    fetchStaff()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(true, bannerMsg)
                    }
                    _bannerEvent.emit(BannerEvent(bannerMsg, true))
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Create staff failed for $name: $errorMsg")
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, errorMsg)
                    }
                    _bannerEvent.emit(BannerEvent("Failed to create $name: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Create staff exception for $name: $errorMsg", t)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, errorMsg)
                }
                _bannerEvent.emit(BannerEvent("Failed to create $name: $errorMsg", false))
            }
        }
    }

    fun updateStaff(
        staffId: String,
        name: String,
        role: String,
        salary: Double,
        phone: String,
        status: String,
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        val bannerMsg = "$name — Staff Details Updated!"
        KioskLogger.user("[UPDATE STAFF] Submitting ID: $staffId -> $name ($role, ₹$salary, $status) to server...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val request = UpdateStaffRequest(
                    name = name,
                    role = role,
                    salary = salary,
                    phone = phone,
                    status = status
                )
                val response = apiService.updateStaff(staffId, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    KioskLogger.user("[UPDATE STAFF SUCCESS] $name updated on server")
                    fetchAllStaff()
                    fetchStaff()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(true, bannerMsg)
                    }
                    _bannerEvent.emit(BannerEvent(bannerMsg, true))
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Update staff failed for $name: $errorMsg")
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, errorMsg)
                    }
                    _bannerEvent.emit(BannerEvent("Failed to update $name: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Update staff exception for $name: $errorMsg", t)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, errorMsg)
                }
                _bannerEvent.emit(BannerEvent("Failed to update $name: $errorMsg", false))
            }
        }
    }

    fun punch(
        staffId: String,
        action: String,
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        val currentList = _staffList.value
        val staffMember = currentList.find { it.id == staffId }
        val staffName = staffMember?.name?.takeIf { it.isNotBlank() } ?: staffId.ifBlank { "Staff" }
        val isClockIn = action.equals("IN", ignoreCase = true)
        val actionText = if (isClockIn) "IN!" else "OUT!"

        KioskLogger.user("[PUNCH REQUEST] Submitting Clock $actionText for $staffName ($staffId)...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.punch(PunchRequest(staffId, action))

                if (response.isSuccessful && response.body() != null) {
                    val punchResp = response.body()!!
                    if (punchResp.success) {
                        val returnedStaff = punchResp.staff
                        if (!returnedStaff.isNullOrEmpty()) {
                            _staffList.value = returnedStaff
                            preferenceManager.saveActiveStaffList(returnedStaff)
                        } else {
                            fetchStaff()
                        }
                        _isOffline.value = false
                        _syncTrigger.emit(Unit)
                        val successMsg = "$staffName — Clocked $actionText"
                        KioskLogger.user("[PUNCH SUCCESS] $successMsg confirmed by server")
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(true, successMsg)
                        }
                        _bannerEvent.emit(BannerEvent(successMsg, true))
                    } else {
                        val errorMsg = punchResp.message ?: "Punch rejected by server"
                        KioskLogger.error("Punch rejected for $staffName: $errorMsg")
                        fetchStaff()
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(false, errorMsg)
                        }
                        _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Punch failed for $staffName: HTTP ${response.code()} - $errorMsg")
                    fetchStaff()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, errorMsg)
                    }
                    _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Punch network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, errorMsg)
                }
                _bannerEvent.emit(BannerEvent("Punch failed for $staffName: $errorMsg", false))
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

        KioskLogger.user("[ADVANCE REQUEST] Submitting ₹$amountStr Advance (Given by $notes) for $staffName...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.addAdvance(AdvanceRequest(staffId = staffId, amount = amount, notes = notes))

                if (response.isSuccessful && response.body() != null) {
                    val advanceResp = response.body()!!
                    if (advanceResp.success) {
                        val successMsg = "₹$amountStr Advance recorded for $staffName"
                        KioskLogger.user("[ADVANCE SUCCESS] $successMsg confirmed by server")
                        _isOffline.value = false
                        fetchStaff()
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(true, successMsg)
                        }
                        _bannerEvent.emit(BannerEvent(successMsg, true))
                    } else {
                        val msg = advanceResp.error ?: advanceResp.message ?: "Failed to record advance"
                        KioskLogger.error("Advance rejected for $staffName: $msg")
                        fetchStaff()
                        withContext(Dispatchers.Main) {
                            onResult?.invoke(false, msg)
                        }
                        _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $msg", false))
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    KioskLogger.error("Advance failed for $staffName: HTTP ${response.code()} - $errorMsg")
                    fetchStaff()
                    withContext(Dispatchers.Main) {
                        onResult?.invoke(false, errorMsg)
                    }
                    _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $errorMsg", false))
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                KioskLogger.error("Advance network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff()
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, errorMsg)
                }
                _bannerEvent.emit(BannerEvent("Advance failed for $staffName: $errorMsg", false))
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
