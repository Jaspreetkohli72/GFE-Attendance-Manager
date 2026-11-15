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
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "gfe_attendance_prefs"
        private const val KEY_BASE_URL = "key_base_url"
        private const val KEY_STAFF_CACHE = "key_staff_cache"
        const val DEFAULT_BASE_URL = "https://galaxyerp.vercel.app"
    }

    fun getBaseUrl(): String {
        val url = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        KioskLogger.sys("PreferenceManager: Read base URL ($url)")
        return url
    }

    fun setBaseUrl(url: String) {
        prefs.edit { putString(KEY_BASE_URL, url) }
        KioskLogger.sys("PreferenceManager: Base URL updated to $url")
    }

    fun saveStaffList(staffList: List<StaffMember>) {
        val jsonString = gson.toJson(staffList)
        prefs.edit { putString(KEY_STAFF_CACHE, jsonString) }
        KioskLogger.sys("PreferenceManager: Saved ${staffList.size} staff members to cache")
    }

    fun getStaffList(): List<StaffMember> {
        val jsonString = prefs.getString(KEY_STAFF_CACHE, null) ?: return emptyList()
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
}
