package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class PunchRequest(
    @SerializedName("staffId")
    val staffId: String = "",
    @SerializedName("action")
    val action: String = ""
)
