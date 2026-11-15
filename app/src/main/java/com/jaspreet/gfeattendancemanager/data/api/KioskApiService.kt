package com.jaspreet.gfeattendancemanager.data.api

import com.jaspreet.gfeattendancemanager.data.model.AdvanceRequest
import com.jaspreet.gfeattendancemanager.data.model.GenericApiResponse
import com.jaspreet.gfeattendancemanager.data.model.PunchRequest
import com.jaspreet.gfeattendancemanager.data.model.PunchResponse
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface KioskApiService {
    @GET("api/kiosk/staff")
    suspend fun getStaff(): Response<List<StaffMember>>

    @POST("api/kiosk/punch")
    suspend fun punch(@Body request: PunchRequest): Response<PunchResponse>

    @POST("api/kiosk/advance")
    suspend fun addAdvance(@Body request: AdvanceRequest): Response<GenericApiResponse>
}
