package com.example.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/**
 * Official ESP-Miner / AxeOS REST API service.
 * Telemetry endpoints are GET; configuration changes use PATCH /api/system
 * strictly after explicit user authorization or manual confirmation.
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

    @PATCH("api/system")
    suspend fun updateSystemConfig(@Body request: SystemConfigPatchRequest): Response<ResponseBody>
}
