package com.jaspreet.gfeattendancemanager.data.api

import com.jaspreet.gfeattendancemanager.data.model.AdvanceRequest
import com.jaspreet.gfeattendancemanager.data.model.CreateStaffRequest
import com.jaspreet.gfeattendancemanager.data.model.CreateStaffResponse
import com.jaspreet.gfeattendancemanager.data.model.GenericApiResponse
import com.jaspreet.gfeattendancemanager.data.model.PunchRequest
import com.jaspreet.gfeattendancemanager.data.model.PunchResponse
import com.jaspreet.gfeattendancemanager.data.model.StaffMember
import com.jaspreet.gfeattendancemanager.data.model.UpdateStaffRequest
import com.jaspreet.gfeattendancemanager.data.model.UpdateStaffResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface KioskApiService {

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("api/kiosk/staff")
    suspend fun getStaff(
        @Query("all") all: Boolean? = null
    ): Response<List<StaffMember>>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("api/kiosk/staff/{id}")
    suspend fun getStaffById(
        @Path("id") id: String
    ): Response<StaffMember>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @POST("api/kiosk/staff")
    suspend fun createStaff(
        @Body request: CreateStaffRequest
    ): Response<CreateStaffResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @PUT("api/kiosk/staff/{id}")
    suspend fun updateStaff(
        @Path("id") id: String,
        @Body request: UpdateStaffRequest
    ): Response<UpdateStaffResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @POST("api/kiosk/punch")
    suspend fun punch(@Body request: PunchRequest): Response<PunchResponse>

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @POST("api/kiosk/advance")
    suspend fun addAdvance(@Body request: AdvanceRequest): Response<GenericApiResponse>
}
