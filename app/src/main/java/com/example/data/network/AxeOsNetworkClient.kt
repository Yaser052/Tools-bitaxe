package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.example.domain.engine.EfficiencyCalculator
import com.example.domain.engine.NetworkValidator
import com.example.domain.model.AsicCapabilities
import com.example.domain.model.EndpointProbeResult
import com.example.domain.model.EndpointStatus
import com.example.domain.model.LiveTelemetry
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

data class DiscoveredBitaxeDevice(
    val ip: String,
    val hostname: String?,
    val deviceModel: String?,
    val boardVersion: String?,
    val asicModel: String?,
    val firmwareVersion: String?,
    val latencyMs: Long
)

data class FullDeviceSnapshot(
    val telemetry: LiveTelemetry,
    val asicCapabilities: AsicCapabilities?,
    val endpointProbes: List<EndpointProbeResult>,
    val firmwareLogs: List<String>,
    val dashboardSamplesCount: Int?
)

sealed class WebSocketEvent {
    data class Opened(val endpoint: String) : WebSocketEvent()
    data class TelemetryMessage(val telemetry: LiveTelemetry) : WebSocketEvent()
    data class LogLine(val line: String) : WebSocketEvent()
    data class ClosedOrFailed(val reason: String) : WebSocketEvent()
}

class AxeOsNetworkClient(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val infoAdapter = moshi.adapter(SystemInfoDto::class.java)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val probeHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    private fun buildService(baseUrl: String, client: OkHttpClient = okHttpClient): AxeOsApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AxeOsApiService::class.java)
    }

    suspend fun fetchSystemInfo(hostIp: String, baseUrl: String): Result<LiveTelemetry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = buildService(baseUrl)
                val startNs = System.nanoTime()
                val resp = service.getSystemInfo()
                val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (!resp.isSuccessful || resp.body() == null) {
                    error("HTTP ${resp.code()} from /api/system/info")
                }
                mapSystemInfoToTelemetry(resp.body()!!, hostIp, latencyMs, "HTTP REST", false)
            }
        }

    suspend fun probeAllEndpoints(hostIp: String, baseUrl: String): FullDeviceSnapshot =
        withContext(Dispatchers.IO) {
            val service = buildService(baseUrl)
            val probes = mutableListOf<EndpointProbeResult>()

            // 1. GET /api/system/info
            val infoStart = System.nanoTime()
            val infoResp = runCatching { service.getSystemInfo() }.getOrNull()
            val infoLatency = ((System.nanoTime() - infoStart) / 1_000_000L).coerceAtLeast(1L)
            val infoBody = if (infoResp?.isSuccessful == true) infoResp.body() else null
            probes += EndpointProbeResult(
                endpoint = "GET /api/system/info",
                protocol = "HTTP",
                status = when {
                    infoResp == null -> EndpointStatus.ERROR
                    infoResp.isSuccessful && infoBody != null -> EndpointStatus.SUPPORTED
                    infoResp.code() == 404 -> EndpointStatus.UNSUPPORTED
                    else -> EndpointStatus.ERROR
                },
                httpCode = infoResp?.code(),
                detail = if (infoBody != null) "${infoLatency}ms • ${infoBody.asicModel ?: "Unknown ASIC"}" else infoResp?.message()
            )

            val telemetry = if (infoBody != null) {
                mapSystemInfoToTelemetry(infoBody, hostIp, infoLatency, "HTTP REST", false)
            } else {
                error("Device unreachable or /api/system/info returned HTTP ${infoResp?.code() ?: "timeout"}")
            }

            // 2. GET /api/system/asic
            val asicResp = runCatching { service.getSystemAsic() }.getOrNull()
            val asicDto = if (asicResp?.isSuccessful == true) asicResp.body() else null
            val capabilities = asicDto?.let {
                AsicCapabilities(
                    asicModel = it.asicModel,
                    deviceModel = it.deviceModel,
                    frequencyOptions = it.frequencyOptions ?: emptyList(),
                    voltageOptions = it.voltageOptions ?: emptyList(),
                    defaultFrequencyMhz = it.defaultFrequency,
                    defaultVoltageMv = it.defaultVoltage,
                    absMaxFrequencyMhz = it.absMaxFrequency,
                    absMaxVoltageMv = it.absMaxVoltage
                )
            }
            probes += EndpointProbeResult(
                endpoint = "GET /api/system/asic",
                protocol = "HTTP",
                status = when {
                    asicResp == null -> EndpointStatus.ERROR
                    asicResp.isSuccessful && asicDto != null -> EndpointStatus.SUPPORTED
                    asicResp.code() == 404 || asicResp.code() == 501 -> EndpointStatus.UNSUPPORTED
                    else -> EndpointStatus.ERROR
                },
                httpCode = asicResp?.code(),
                detail = capabilities?.let {
                    "Freq opts: ${it.frequencyOptions.size}, Volt opts: ${it.voltageOptions.size}"
                } ?: "Not supported by this firmware"
            )

            // 3. GET /api/system/statistics
            val statsResp = runCatching { service.getSystemStatistics() }.getOrNull()
            val statsBody = if (statsResp?.isSuccessful == true) statsResp.body()?.string() else null
            val statsCount = statsBody?.let { parseStatisticsCount(it) }
            probes += EndpointProbeResult(
                endpoint = "GET /api/system/statistics",
                protocol = "HTTP",
                status = when {
                    statsResp == null -> EndpointStatus.ERROR
                    statsResp.isSuccessful && !statsBody.isNullOrBlank() -> EndpointStatus.SUPPORTED
                    statsResp.code() == 404 || statsResp.code() == 501 -> EndpointStatus.UNSUPPORTED
                    else -> EndpointStatus.ERROR
                },
                httpCode = statsResp?.code(),
                detail = statsCount?.let { "$it firmware points" } ?: "Optional endpoint"
            )

            // 4. GET /api/system/statistics/dashboard
            val dashResp = runCatching { service.getDashboardStatistics() }.getOrNull()
            val dashBody = if (dashResp?.isSuccessful == true) dashResp.body()?.string() else null
            val dashCount = dashBody?.let { parseStatisticsCount(it) }
            probes += EndpointProbeResult(
                endpoint = "GET /api/system/statistics/dashboard",
                protocol = "HTTP",
                status = when {
                    dashResp == null -> EndpointStatus.ERROR
                    dashResp.isSuccessful && !dashBody.isNullOrBlank() -> EndpointStatus.SUPPORTED
                    dashResp.code() == 404 || dashResp.code() == 501 -> EndpointStatus.UNSUPPORTED
                    else -> EndpointStatus.ERROR
                },
                httpCode = dashResp?.code(),
                detail = dashCount?.let { "$it dashboard samples" } ?: "Optional endpoint"
            )

            // 5. GET /api/system/logs
            val logsResp = runCatching { service.getSystemLogs() }.getOrNull()
            val logsRaw = if (logsResp?.isSuccessful == true) logsResp.body()?.string() else null
            val parsedLogs = logsRaw?.let { parseLogsResponse(it) } ?: emptyList()
            probes += EndpointProbeResult(
                endpoint = "GET /api/system/logs",
                protocol = "HTTP",
                status = when {
                    logsResp == null -> EndpointStatus.ERROR
                    logsResp.isSuccessful -> EndpointStatus.SUPPORTED
                    logsResp.code() == 404 || logsResp.code() == 501 -> EndpointStatus.UNSUPPORTED
                    else -> EndpointStatus.ERROR
                },
                httpCode = logsResp?.code(),
                detail = if (parsedLogs.isNotEmpty()) "${parsedLogs.size} lines" else "Optional endpoint"
            )

            // 6. WebSocket /api/ws/live (initial status marked NOT_PROBED until stream opens)
            probes += EndpointProbeResult(
                endpoint = "WS /api/ws/live",
                protocol = "WebSocket",
                status = EndpointStatus.NOT_PROBED,
                detail = "Live telemetry & log stream"
            )

            FullDeviceSnapshot(
                telemetry = telemetry,
                asicCapabilities = capabilities,
                endpointProbes = probes,
                firmwareLogs = parsedLogs,
                dashboardSamplesCount = dashCount ?: statsCount
            )
        }

    fun observeLiveWebSocket(hostIp: String): Flow<WebSocketEvent> = callbackFlow {
        val wsUrl = "ws://$hostIp/api/ws/live"
        val request = Request.Builder().url(wsUrl).build()
        val socket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trySend(WebSocketEvent.Opened("/api/ws/live"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val trimmed = text.trim()
                    if (trimmed.startsWith("{")) {
                        val dto = runCatching { infoAdapter.fromJson(trimmed) }.getOrNull()
                        if (dto != null && (dto.hashRate != null || dto.power != null || dto.temp != null)) {
                            trySend(
                                WebSocketEvent.TelemetryMessage(
                                    mapSystemInfoToTelemetry(
                                        dto = dto,
                                        hostIp = hostIp,
                                        latencyMs = null,
                                        transport = "WebSocket /api/ws/live",
                                        isWs = true
                                    )
                                )
                            )
                            return
                        }
                    }
                    if (trimmed.isNotEmpty()) {
                        trySend(WebSocketEvent.LogLine(trimmed))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    trySend(WebSocketEvent.ClosedOrFailed("WebSocket closed ($code): $reason"))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    trySend(WebSocketEvent.ClosedOrFailed(t.message ?: "WebSocket connection unavailable"))
                }
            }
        )

        awaitClose {
            socket.close(1000, "Client stopped")
        }
    }

    /**
     * Scans the device's local Wi-Fi/LAN /24 subnet in parallel batches for Bitaxe AxeOS nodes.
     */
    suspend fun discoverLocalBitaxeDevices(): List<DiscoveredBitaxeDevice> =
        withContext(Dispatchers.IO) {
            val prefix = detectLocalIpv4SubnetPrefix() ?: "192.168.1"
            val candidates = (1..254).map { "$prefix.$it" }
            val semaphore = Semaphore(28)

            coroutineScope {
                candidates.map { ip ->
                    async {
                        semaphore.withPermit {
                            probeSingleCandidate(ip)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }

    private suspend fun probeSingleCandidate(ip: String): DiscoveredBitaxeDevice? {
        if (NetworkValidator.validateLocalMinerAddress(ip) !is com.example.domain.engine.IpValidationResult.Valid) {
            return null
        }
        return runCatching {
            val service = buildService("http://$ip/", probeHttpClient)
            val start = System.nanoTime()
            val resp = service.getSystemInfo()
            val latency = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
            val body = resp.body()
            if (resp.isSuccessful && body != null &&
                (body.asicModel != null || body.hashRate != null || body.boardVersion != null)
            ) {
                DiscoveredBitaxeDevice(
                    ip = ip,
                    hostname = body.hostname,
                    deviceModel = body.deviceModel,
                    boardVersion = body.boardVersion,
                    asicModel = body.asicModel,
                    firmwareVersion = body.version,
                    latencyMs = latency
                )
            } else {
                null
            }
        }.getOrNull()
    }

    private fun detectLocalIpv4SubnetPrefix(): String? {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching null
            val activeNetwork = cm.activeNetwork ?: return@runCatching null
            val linkProps: LinkProperties = cm.getLinkProperties(activeNetwork) ?: return@runCatching null
            val ipv4 = linkProps.linkAddresses
                .mapNotNull { it.address as? Inet4Address }
                .firstOrNull { !it.isLoopbackAddress }
                ?: return@runCatching null
            val parts = ipv4.hostAddress?.split(".") ?: return@runCatching null
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
        }.getOrNull()
    }

    private fun mapSystemInfoToTelemetry(
        dto: SystemInfoDto,
        hostIp: String,
        latencyMs: Long?,
        transport: String,
        isWs: Boolean
    ): LiveTelemetry {
        // ESP-Miner reports hashRate in GH/s; convert strictly to TH/s (1 TH/s = 1000 GH/s).
        val currentThs = EfficiencyCalculator.hashrateGhsToThs(dto.hashRate)
        val avgThs = EfficiencyCalculator.hashrateGhsToThs(dto.hashRate10m ?: dto.hashRate1m ?: dto.hashRate)
        val expectedThs = EfficiencyCalculator.hashrateGhsToThs(dto.expectedHashrate)
        val efficiency = EfficiencyCalculator.calculateEfficiencyJth(dto.power, currentThs)

        val fallbackFlag = when (val raw = dto.isUsingFallbackStratum) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> null
        }

        val errPct = dto.asicErrorPercentage ?: dto.errorPercentage
        val stale = dto.staleShares ?: dto.sharesStale

        return LiveTelemetry(
            timestampMillis = System.currentTimeMillis(),
            deviceIp = hostIp,
            hostname = dto.hostname,
            deviceModel = dto.deviceModel,
            boardVersion = dto.boardVersion,
            asicModel = dto.asicModel,
            asicCount = dto.asicCount,
            smallCoreCount = dto.smallCoreCount,
            firmwareVersion = dto.version,
            axeOsVersion = dto.axeOsVersion,
            idfVersion = dto.idfVersion,
            macAddress = dto.macAddr,
            ssid = dto.ssid,
            wifiStatus = dto.wifiStatus,
            wifiRssi = dto.wifiRssi,
            currentHashrateGhs = dto.hashRate,
            currentHashrateThs = currentThs,
            averageHashrateThs = avgThs,
            expectedHashrateThs = expectedThs,
            frequencyMhz = dto.frequency?.toInt(),
            coreVoltageTargetMv = dto.coreVoltage,
            coreVoltageActualMv = dto.coreVoltageActual,
            inputVoltageMv = dto.voltage,
            currentMa = dto.current,
            powerWatts = dto.power,
            efficiencyJth = efficiency,
            asicTempC = dto.temp,
            vrTempC = dto.vrTemp,
            asicErrorPct = errPct,
            acceptedShares = dto.sharesAccepted,
            rejectedShares = dto.sharesRejected,
            staleShares = stale,
            bestDiff = dto.bestDiff,
            bestSessionDiff = dto.bestSessionDiff,
            fanRpm = dto.fanRpm,
            fanSpeedPct = dto.fanSpeed,
            autoFanSpeed = dto.autoFanSpeed?.let { it != 0 },
            uptimeSeconds = dto.uptimeSeconds,
            stratumUrl = dto.stratumUrl,
            stratumPort = dto.stratumPort,
            stratumUser = dto.stratumUser,
            isFallbackStratum = fallbackFlag,
            poolDifficulty = dto.poolDifficulty,
            overheatMode = (dto.overheatMode ?: 0) != 0,
            powerFault = dto.powerFault,
            latencyMs = latencyMs,
            transportType = transport,
            isWebSocketLive = isWs
        )
    }

    private fun parseStatisticsCount(rawJson: String): Int? {
        return runCatching {
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed).length()
            } else {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("statistics") -> obj.getJSONArray("statistics").length()
                    obj.has("data") -> obj.getJSONArray("data").length()
                    else -> obj.length()
                }
            }
        }.getOrNull()
    }

    private fun parseLogsResponse(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).map { arr.getString(it) }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val arr = obj.optJSONArray("logs")
                if (arr != null) {
                    (0 until arr.length()).map { arr.getString(it) }
                } else {
                    trimmed.lines().filter { it.isNotBlank() }
                }
            } else {
                trimmed.lines().filter { it.isNotBlank() }
            }
        }.getOrElse { trimmed.lines().filter { it.isNotBlank() } }
    }
}
