package com.jaspreet.gfeattendancemanager.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jaspreet.gfeattendancemanager.data.model.StaffMember

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "gfe_attendance_prefs"
        private const val KEY_BASE_URL = "key_base_url"
        private const val PREF_CACHED_ACTIVE_STAFF = "key_active_staff_cache"
        private const val PREF_CACHED_ALL_STAFF = "key_all_staff_cache"
        const val DEFAULT_BASE_URL = "https://galaxyerp.vercel.app/"
    }

    fun getBaseUrl(): String {
        val rawUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        var url = rawUrl.trim()
        if (url.isBlank()) {
            url = DEFAULT_BASE_URL
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        KioskLogger.sys("PreferenceManager: Read base URL ($url)")
        return url
    }

    fun setBaseUrl(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            cleanUrl = DEFAULT_BASE_URL
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        if (!cleanUrl.endsWith("/")) {
            cleanUrl = "$cleanUrl/"
        }
        prefs.edit { putString(KEY_BASE_URL, cleanUrl) }
        KioskLogger.sys("PreferenceManager: Base URL updated to $cleanUrl")
    }

    // Active Staff (Main Kiosk Screen)
    fun saveActiveStaffList(staffList: List<StaffMember>) {
        try {
            val jsonString = gson.toJson(staffList)
            prefs.edit { putString(PREF_CACHED_ACTIVE_STAFF, jsonString) }
            KioskLogger.sys("PreferenceManager: Saved ${staffList.size} active staff members to cache")
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Failed to save active staff list to cache", e)
        }
    }

    fun getActiveStaffList(): List<StaffMember> {
        val jsonString = prefs.getString(PREF_CACHED_ACTIVE_STAFF, null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<StaffMember>>() {}.type
            val list = gson.fromJson<List<StaffMember>>(jsonString, type) ?: emptyList()
            KioskLogger.sys("PreferenceManager: Read ${list.size} active staff members from cache")
            list
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Exception reading cached active staff list", e)
            emptyList()
        }
    }

    // All Staff (Admin Staff Management Screen)
    fun saveAllStaffList(staffList: List<StaffMember>) {
        try {
            val jsonString = gson.toJson(staffList)
            prefs.edit { putString(PREF_CACHED_ALL_STAFF, jsonString) }
            KioskLogger.sys("PreferenceManager: Saved ${staffList.size} total staff members to admin cache")
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Failed to save all staff list to cache", e)
        }
    }

    fun getAllStaffList(): List<StaffMember> {
        val jsonString = prefs.getString(PREF_CACHED_ALL_STAFF, null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<StaffMember>>() {}.type
            val list = gson.fromJson<List<StaffMember>>(jsonString, type) ?: emptyList()
            KioskLogger.sys("PreferenceManager: Read ${list.size} total staff members from admin cache")
            list
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Exception reading cached all staff list", e)
            emptyList()
        }
    }

    // Backward compatibility aliases
    fun saveStaffList(staffList: List<StaffMember>) = saveActiveStaffList(staffList)
    fun getStaffList(): List<StaffMember> = getActiveStaffList()
    fun getCachedStaffList(): List<StaffMember> = getActiveStaffList()
}
