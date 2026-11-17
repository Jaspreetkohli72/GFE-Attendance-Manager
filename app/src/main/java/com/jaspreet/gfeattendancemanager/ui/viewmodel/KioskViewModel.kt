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
    private var fetchJob: Job? = null
    @Volatile
    private var lastMutationTime: Long = 0L

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
                    fetchStaff(forceRefresh = false)
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
                member.name.orEmpty().lowercase(Locale.getDefault())
            }
        )
    }

    fun fetchStaff(forceRefresh: Boolean = false) {
        if (_isLoading.value && !forceRefresh) {
            return
        }
        if (forceRefresh) {
            fetchJob?.cancel()
        }
        _isLoading.value = true
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.getStaff(all = false)

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    // Don't let a stale in-flight poll overwrite a recent mutation
                    if (System.currentTimeMillis() - lastMutationTime > 3000L || forceRefresh) {
                        if (_staffList.value != list) {
                            KioskLogger.sys("Active staff data updated (${list.size} members)")
                            _staffList.value = list
                            preferenceManager.saveActiveStaffList(list)
                            _syncTrigger.emit(Unit)
                        }
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
                if (t is kotlinx.coroutines.CancellationException) throw t
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
                if (t is kotlinx.coroutines.CancellationException) throw t
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
        lastMutationTime = System.currentTimeMillis()
        fetchJob?.cancel()
        val bannerMsg = "$name — Staff Created!"
        KioskLogger.user("[CREATE STAFF] Submitting $name ($role, ₹$salary, $status) to server...")

        viewModelScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var finalMsg = ""
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
                    isSuccess = true
                    finalMsg = bannerMsg
                    KioskLogger.user("[CREATE STAFF SUCCESS] $name confirmed by server")
                    fetchAllStaff()
                    fetchStaff(forceRefresh = true)
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    finalMsg = "Failed to create $name: $errorMsg"
                    KioskLogger.error(finalMsg)
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                finalMsg = "Failed to create $name: $errorMsg"
                KioskLogger.error("Create staff exception for $name: $errorMsg", t)
            }

            withContext(Dispatchers.Main) {
                try {
                    onResult?.invoke(isSuccess, finalMsg)
                } catch (e: Exception) {
                    KioskLogger.error("UI callback error in createStaff", e)
                }
            }
            _bannerEvent.emit(BannerEvent(finalMsg, isSuccess))
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
        lastMutationTime = System.currentTimeMillis()
        fetchJob?.cancel()
        val bannerMsg = "$name — Staff Details Updated!"
        KioskLogger.user("[UPDATE STAFF] Submitting ID: $staffId -> $name ($role, ₹$salary, $status) to server...")

        viewModelScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var finalMsg = ""
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
                    isSuccess = true
                    finalMsg = bannerMsg
                    KioskLogger.user("[UPDATE STAFF SUCCESS] $name updated on server")
                    fetchAllStaff()
                    fetchStaff(forceRefresh = true)
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    finalMsg = "Failed to update $name: $errorMsg"
                    KioskLogger.error(finalMsg)
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                finalMsg = "Failed to update $name: $errorMsg"
                KioskLogger.error("Update staff exception for $name: $errorMsg", t)
            }

            withContext(Dispatchers.Main) {
                try {
                    onResult?.invoke(isSuccess, finalMsg)
                } catch (e: Exception) {
                    KioskLogger.error("UI callback error in updateStaff", e)
                }
            }
            _bannerEvent.emit(BannerEvent(finalMsg, isSuccess))
        }
    }

    fun punch(
        staffId: String,
        action: String,
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        lastMutationTime = System.currentTimeMillis()
        fetchJob?.cancel()
        val currentList = _staffList.value
        val staffMember = currentList.find { it.id == staffId }
        val staffName = staffMember?.name?.takeIf { it.isNotBlank() } ?: staffId.ifBlank { "Staff" }
        val isClockIn = action.equals("IN", ignoreCase = true)
        val actionText = if (isClockIn) "IN!" else "OUT!"

        KioskLogger.user("[PUNCH REQUEST] Submitting Clock $actionText for $staffName ($staffId)...")

        viewModelScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var finalMsg = ""
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.punch(PunchRequest(staffId, action))

                if (response.isSuccessful && response.body() != null) {
                    val punchResp = response.body()!!
                    if (punchResp.success) {
                        isSuccess = true
                        finalMsg = "$staffName — Clocked $actionText"
                        val returnedStaff = punchResp.staff
                        if (!returnedStaff.isNullOrEmpty()) {
                            _staffList.value = returnedStaff
                            preferenceManager.saveActiveStaffList(returnedStaff)
                        } else {
                            fetchStaff(forceRefresh = true)
                        }
                        _isOffline.value = false
                        _syncTrigger.emit(Unit)
                        KioskLogger.user("[PUNCH SUCCESS] $finalMsg confirmed by server")
                    } else {
                        val errorMsg = punchResp.message ?: "Punch rejected by server"
                        finalMsg = "Punch failed for $staffName: $errorMsg"
                        KioskLogger.error(finalMsg)
                        fetchStaff(forceRefresh = true)
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    finalMsg = "Punch failed for $staffName: HTTP ${response.code()} - $errorMsg"
                    KioskLogger.error(finalMsg)
                    fetchStaff(forceRefresh = true)
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                finalMsg = "Punch failed for $staffName: $errorMsg"
                KioskLogger.error("Punch network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff(forceRefresh = true)
            }

            withContext(Dispatchers.Main) {
                try {
                    onResult?.invoke(isSuccess, finalMsg)
                } catch (e: Exception) {
                    KioskLogger.error("UI callback error in punch", e)
                }
            }
            _bannerEvent.emit(BannerEvent(finalMsg, isSuccess))
        }
    }

    fun addAdvance(
        staffId: String,
        amount: Double,
        notes: String = "Sandeep",
        onResult: ((isSuccess: Boolean, message: String) -> Unit)? = null
    ) {
        lastMutationTime = System.currentTimeMillis()
        fetchJob?.cancel()
        val staffName = _staffList.value.find { it.id == staffId }?.name?.takeIf { it.isNotBlank() } ?: staffId.ifBlank { "Staff" }
        val amountStr = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()

        KioskLogger.user("[ADVANCE REQUEST] Submitting ₹$amountStr Advance (Given by $notes) for $staffName...")

        viewModelScope.launch(Dispatchers.IO) {
            var isSuccess = false
            var finalMsg = ""
            try {
                val baseUrl = preferenceManager.getBaseUrl()
                val apiService = ApiClient.getService(baseUrl)
                val response = apiService.addAdvance(AdvanceRequest(staffId = staffId, amount = amount, notes = notes))

                if (response.isSuccessful && response.body() != null) {
                    val advanceResp = response.body()!!
                    if (advanceResp.success) {
                        isSuccess = true
                        finalMsg = "₹$amountStr Advance recorded for $staffName"
                        KioskLogger.user("[ADVANCE SUCCESS] $finalMsg confirmed by server")
                        _isOffline.value = false
                        fetchStaff(forceRefresh = true)
                    } else {
                        val msg = advanceResp.error ?: advanceResp.message ?: "Failed to record advance"
                        finalMsg = "Advance failed for $staffName: $msg"
                        KioskLogger.error(finalMsg)
                        fetchStaff(forceRefresh = true)
                    }
                } else {
                    val errorRaw = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(errorRaw, response.code())
                    finalMsg = "Advance failed for $staffName: HTTP ${response.code()} - $errorMsg"
                    KioskLogger.error(finalMsg)
                    fetchStaff(forceRefresh = true)
                }
            } catch (t: Throwable) {
                val errorMsg = t.localizedMessage ?: "Network error"
                finalMsg = "Advance failed for $staffName: $errorMsg"
                KioskLogger.error("Advance network exception for $staffName: $errorMsg", t)
                _isOffline.value = true
                fetchStaff(forceRefresh = true)
            }

            withContext(Dispatchers.Main) {
                try {
                    onResult?.invoke(isSuccess, finalMsg)
                } catch (e: Exception) {
                    KioskLogger.error("UI callback error in addAdvance", e)
                }
            }
            _bannerEvent.emit(BannerEvent(finalMsg, isSuccess))
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        pollingJob = null
        fetchJob?.cancel()
        fetchJob = null
    }
}
