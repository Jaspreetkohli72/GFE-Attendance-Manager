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
        private const val KEY_STAFF_CACHE = "key_staff_cache"
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

    fun saveStaffList(staffList: List<StaffMember>) {
        try {
            val jsonString = gson.toJson(staffList)
            prefs.edit { putString(KEY_STAFF_CACHE, jsonString) }
            KioskLogger.sys("PreferenceManager: Saved ${staffList.size} staff members to cache")
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Failed to save staff list to cache", e)
        }
    }

    fun getStaffList(): List<StaffMember> {
        val jsonString = prefs.getString(KEY_STAFF_CACHE, null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<StaffMember>>() {}.type
            val list = gson.fromJson<List<StaffMember>>(jsonString, type) ?: emptyList()
            KioskLogger.sys("PreferenceManager: Read ${list.size} staff members from cache")
            list
        } catch (e: Exception) {
            KioskLogger.error("PreferenceManager: Exception reading cached staff list", e)
            emptyList()
        }
    }

    @Suppress("unused")
    fun getCachedStaffList(): List<StaffMember> = getStaffList()
}
