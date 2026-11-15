package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class StaffMember(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("clockedIn")
    val clockedIn: Boolean = false,
    @SerializedName("since")
    val since: String? = null
)
