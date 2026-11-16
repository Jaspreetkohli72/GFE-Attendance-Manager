package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class StaffMember(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("salary")
    val salary: Double? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("clockedIn")
    val clockedIn: Boolean = false,
    @SerializedName("since")
    val since: String? = null
) {
    val isActuallyClockedIn: Boolean
        get() = clockedIn

    val isTerminated: Boolean
        get() = status?.startsWith("Terminated", ignoreCase = true) == true

    val isOnLeave: Boolean
        get() = status?.equals("On Leave", ignoreCase = true) == true

    val isAvailable: Boolean
        get() = status == null || status.equals("Available", ignoreCase = true)
}
