package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class PunchResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("staff")
    val staff: List<StaffMember>? = null
)
