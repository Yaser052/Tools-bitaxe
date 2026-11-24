package com.example.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

/**
 * Strictly READ-ONLY AxeOS / ESP-Miner API service.
 * RECOMMENDATION-ONLY MODE: No PATCH, POST, PUT, or DELETE endpoints exist in this client.
 */
interface AxeOsApiService {

    @GET("api/system/info")
    suspend fun getSystemInfo(): Response<SystemInfoDto>

    @GET("api/system/asic")
    suspend fun getSystemAsic(): Response<SystemAsicDto>

    @GET("api/system/statistics")
    suspend fun getSystemStatistics(): Response<ResponseBody>

    @GET("api/system/statistics/dashboard")
    suspend fun getDashboardStatistics(): Response<ResponseBody>

    @GET("api/system/logs")
    suspend fun getSystemLogs(): Response<ResponseBody>
}
