package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class CreateStaffRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("role")
    val role: String? = "Helper",
    @SerializedName("salary")
    val salary: Double? = 0.0,
    @SerializedName("phone")
    val phone: String? = "",
    @SerializedName("status")
    val status: String? = "Available"
)

data class CreateStaffResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("staff")
    val staff: StaffMember? = null
)

data class UpdateStaffRequest(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("salary")
    val salary: Double? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("status")
    val status: String? = null
)

data class UpdateStaffResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("staff")
    val staff: StaffMember? = null
)
