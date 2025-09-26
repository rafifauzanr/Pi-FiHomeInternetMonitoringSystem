package com.example.cobacoba

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


data class DashboardData(
    val total_devices: Int,
    val total_alerts: Int,
    val connection_time: String,
)

data class WebActivityLog(
    val timestamp: String,
    val device_ip: String,
    val url: String,
    val base_domain: String,
    val status: String,
    val domain_status: String,
    val domain_category: String,
    val content_status: String,
    val detected_content: List<String>,
    val is_safe: Boolean
)

data class LogResponse(
    val summary: LogsSummary,
    val devices_summary: Map<String, DeviceSummary>,
    val logs: List<WebActivityLog>
)

data class LogsSummary(
    val total_logs_in_history: Int,
    val logs_returned: Int,
    val safe_requests: Int,
    val unsafe_requests: Int,
    val last_updated: String
)

data class DeviceSummary(
    val total_requests: Int,
    val safe_requests: Int,
    val unsafe_requests: Int,
    val domains_visited: List<String>,
    val unique_domains_count: Int
)


data class AlertsResponse(
    val total_alerts: Int,
    val blocked_websites: BlockedWebsites,
    val negative_content_websites: NegativeContentWebsites,
    val summary: AlertsSummary
)

data class BlockedWebsites(
    val count: Int,
    val websites: List<BlockedWebsite>
)

data class NegativeContentWebsites(
    val count: Int,
    val websites: List<NegativeContentWebsite>
)

data class BlockedWebsite(
    val url: String,
    val base_domain: String,
    val status: String,
    val category: String,
    val timestamp: String,
    val detected_keywords: DetectedKeywords
)

data class NegativeContentWebsite(
    val url: String,
    val status: String,
    val kategori_konten: List<String>,
    val timestamp: String,
    val detected_keywords: DetectedKeywords
)

data class DetectedKeywords(
    val pornografi: List<String>,
    val judi: List<String>,
    val kasar: List<String>
)

data class AlertsSummary(
    val total_blocked_websites: Int,
    val total_negative_content_websites: Int,
    val last_updated: String
)

data class ConnectedDeviceResponse(
    val total_devices: Int,
    val devices: Map<String, DeviceDetail>
)

data class DeviceDetail(
    val first_connected: String,
    val last_connected: String,
    val connection_duration_seconds: Double,
    val connection_duration_formatted: String,
    val request_count: Int,
    val domains_visited: List<String>,
    val domains_count: Int,
    val status: String
)

data class RenameRequest(
    val ip:String,
    val device_name: String
)

data class RenameResponse(
    val success: Boolean,
    val ip: String,
    val device_name: String
)

interface ApiService {
    @GET("dashboard")
    suspend fun getDashboardData(): Response<DashboardData>

    @GET("web_activity_logs")
    suspend fun getActivityLogs(): Response<LogResponse>

    @GET("alerts_info")
    suspend fun getAlertsInfo(): Response<AlertsResponse>

    @POST("rename_device")
    fun renameDevice(@Body body: RenameRequest): Call<RenameResponse>

    @GET("connected_devices")
    suspend fun getConnectedDevices(): Response<ConnectedDeviceResponse>
}