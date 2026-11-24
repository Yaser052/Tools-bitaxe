package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Real ESP-Miner / AxeOS /api/system/info response schema.
 * All fields are nullable to safely handle differences across firmware versions.
 */
@JsonClass(generateAdapter = true)
data class SystemInfoDto(
    @Json(name = "power") val power: Double? = null,
    @Json(name = "voltage") val voltage: Double? = null,
    @Json(name = "current") val current: Double? = null,
    @Json(name = "temp") val temp: Double? = null,
    @Json(name = "vrTemp") val vrTemp: Double? = null,
    @Json(name = "hashRate") val hashRate: Double? = null,
    @Json(name = "hashRate_1m") val hashRate1m: Double? = null,
    @Json(name = "hashRate_10m") val hashRate10m: Double? = null,
    @Json(name = "hashRate_1h") val hashRate1h: Double? = null,
    @Json(name = "expectedHashrate") val expectedHashrate: Double? = null,
    @Json(name = "errorPercentage") val errorPercentage: Double? = null,
    @Json(name = "asicErrorPercentage") val asicErrorPercentage: Double? = null,
    @Json(name = "bestDiff") val bestDiff: String? = null,
    @Json(name = "bestSessionDiff") val bestSessionDiff: String? = null,
    @Json(name = "sharesAccepted") val sharesAccepted: Long? = null,
    @Json(name = "sharesRejected") val sharesRejected: Long? = null,
    @Json(name = "staleShares") val staleShares: Long? = null,
    @Json(name = "sharesStale") val sharesStale: Long? = null,
    @Json(name = "frequency") val frequency: Double? = null,
    @Json(name = "coreVoltage") val coreVoltage: Int? = null,
    @Json(name = "coreVoltageActual") val coreVoltageActual: Int? = null,
    @Json(name = "ASICModel") val asicModel: String? = null,
    @Json(name = "asicCount") val asicCount: Int? = null,
    @Json(name = "smallCoreCount") val smallCoreCount: Int? = null,
    @Json(name = "boardVersion") val boardVersion: String? = null,
    @Json(name = "deviceModel") val deviceModel: String? = null,
    @Json(name = "hostname") val hostname: String? = null,
    @Json(name = "macAddr") val macAddr: String? = null,
    @Json(name = "ssid") val ssid: String? = null,
    @Json(name = "wifiStatus") val wifiStatus: String? = null,
    @Json(name = "wifiRSSI") val wifiRssi: Int? = null,
    @Json(name = "stratumURL") val stratumUrl: String? = null,
    @Json(name = "stratumPort") val stratumPort: Int? = null,
    @Json(name = "stratumUser") val stratumUser: String? = null,
    @Json(name = "isUsingFallbackStratum") val isUsingFallbackStratum: Any? = null,
    @Json(name = "poolDifficulty") val poolDifficulty: Double? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "axeOSVersion") val axeOsVersion: String? = null,
    @Json(name = "idfVersion") val idfVersion: String? = null,
    @Json(name = "uptimeSeconds") val uptimeSeconds: Long? = null,
    @Json(name = "fanrpm") val fanRpm: Int? = null,
    @Json(name = "fanspeed") val fanSpeed: Double? = null,
    @Json(name = "autofanspeed") val autoFanSpeed: Int? = null,
    @Json(name = "overheat_mode") val overheatMode: Int? = null,
    @Json(name = "power_fault") val powerFault: String? = null
)

/**
 * Real ESP-Miner / AxeOS /api/system/asic response schema.
 */
@JsonClass(generateAdapter = true)
data class SystemAsicDto(
    @Json(name = "ASICModel") val asicModel: String? = null,
    @Json(name = "deviceModel") val deviceModel: String? = null,
    @Json(name = "frequencyOptions") val frequencyOptions: List<Int>? = null,
    @Json(name = "voltageOptions") val voltageOptions: List<Int>? = null,
    @Json(name = "defaultFrequency") val defaultFrequency: Int? = null,
    @Json(name = "defaultVoltage") val defaultVoltage: Int? = null,
    @Json(name = "absMaxFrequency") val absMaxFrequency: Int? = null,
    @Json(name = "absMaxVoltage") val absMaxVoltage: Int? = null
)
