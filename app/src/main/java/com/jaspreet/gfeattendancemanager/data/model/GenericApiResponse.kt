package com.jaspreet.gfeattendancemanager.data.model

import com.google.gson.annotations.SerializedName

data class GenericApiResponse(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error")
    val error: String? = null
)
