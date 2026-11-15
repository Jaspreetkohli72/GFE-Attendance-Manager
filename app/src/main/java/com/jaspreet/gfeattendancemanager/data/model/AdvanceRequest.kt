package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class AdvanceRequest(
    @SerializedName("staffId")
    val staffId: String = "",
    @SerializedName("amount")
    val amount: Double = 0.0,
    @SerializedName("notes")
    val notes: String? = null
)
