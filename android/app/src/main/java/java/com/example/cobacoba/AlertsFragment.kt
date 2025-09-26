package com.example.cobacoba

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat

class AlertsFragment : Fragment() {

    private lateinit var alertsScrollView: ScrollView
    private lateinit var alertsTextView: TextView
    private lateinit var summaryTextView: TextView

    private val TAG = "AlertsFragment"
    private var shouldAutoRefresh = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        alertsScrollView = view.findViewById(R.id.alerts_scroll_view)
        alertsTextView = view.findViewById(R.id.alerts_text_view)
        summaryTextView = view.findViewById(R.id.summary_text_view)

        alertsTextView.movementMethod = LinkMovementMethod.getInstance()

        startAlertsFetching()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shouldAutoRefresh = false
    }

    private fun startAlertsFetching() {
        lifecycleScope.launch {
            while (shouldAutoRefresh) {
                fetchAlerts()
                delay(3000)
            }
        }
    }

    private suspend fun fetchAlerts() {
        try {
            val response = RetrofitClient.apiService.getAlertsInfo()
            if (response.isSuccessful) {
                response.body()?.let { alertsResponse ->
                    activity?.runOnUiThread {
                        updateAlertsDisplay(alertsResponse)
                        alertsScrollView.post {
                            alertsScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to fetch alerts: ${response.code()}")
                activity?.runOnUiThread {
                    summaryTextView.text = "Error: Failed to fetch alerts (${response.code()})"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching alerts: ${e.message}")
            activity?.runOnUiThread {
                summaryTextView.text = "Error: ${e.message}"
            }
        }
    }

    private fun updateAlertsDisplay(alertsResponse: AlertsResponse) {
        val summary = alertsResponse.summary
        val summaryText = """
            Total Alerts: ${alertsResponse.total_alerts}
            🚫 Blocked Websites: ${summary.total_blocked_websites}
            ⚠️ Negative Content: ${summary.total_negative_content_websites}
            Last Updated: ${summary.last_updated}
        """.trimIndent()

        summaryTextView.text = summaryText

        val alertsText = StringBuilder()

        if (alertsResponse.blocked_websites.websites.isNotEmpty()) {
            alertsText.append("🚫 BLOCKED WEBSITES\n")
            alertsText.append("═".repeat(50)).append("\n\n")
            alertsResponse.blocked_websites.websites.forEach { website ->
                alertsText.append(formatBlockedWebsite(website)).append("<br><br>")
            }
        }

        if (alertsResponse.negative_content_websites.websites.isNotEmpty()) {
            alertsText.append("<br>⚠️ WEBSITES WITH NEGATIVE CONTENT\n")
            alertsText.append("═".repeat(50)).append("\n\n")
            alertsResponse.negative_content_websites.websites.forEach { website ->
                alertsText.append(formatNegativeContentWebsite(website)).append("<br><br>")
            }
        }

        if (alertsResponse.total_alerts == 0) {
            alertsText.append("✅ No security alerts detected!<br>All web activity appears to be safe.<br>")
        }

        alertsTextView.text = HtmlCompat.fromHtml(alertsText.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun formatBlockedWebsite(website: BlockedWebsite): String {
        val timestamp = formatTimestamp(website.timestamp)
        val detectedKeywords = formatDetectedKeywords(website.detected_keywords)


        val statusIndicator = if (website.status.contains("Website Diblokir Kominfo", ignoreCase = true)) {
            "🚫 Website Diblokir Kominfo"
        } else {
            "🚫 ${website.status}"
        }

        return """
            ⚠️ [$timestamp]<br>
            🌐 URL: <a href=\"${website.url}\">${website.url}</a><br>
            📄 Status: $statusIndicator<br>
            🏷️ Content Type: ${website.category}<br>
            ${if (detectedKeywords.isNotEmpty()) "🔍 Keywords: $detectedKeywords<br>" else ""}
            ───────────────────────────────────
        """.trimIndent()
    }

    private fun formatNegativeContentWebsite(website: NegativeContentWebsite): String {
        val timestamp = formatTimestamp(website.timestamp)
        val detectedKeywords = formatDetectedKeywords(website.detected_keywords)
        val contentCategories = website.kategori_konten.joinToString(", ")


        val statusIndicator = "⚠️ Website Aman Mengandung Konten Negatif"

        return """
            ⚠️ [$timestamp]<br>
            🌐 URL: <a href=\"${website.url}\">${website.url}</a><br>
            📄 Status: $statusIndicator<br>
            🏷️ Content Type: $contentCategories<br>
            ${if (detectedKeywords.isNotEmpty()) "🔍 Keywords: $detectedKeywords<br>" else ""}
            ───────────────────────────────────
        """.trimIndent()
    }

    private fun formatDetectedKeywords(detected: DetectedKeywords): String {
        val list = mutableListOf<String>()
        if (detected.pornografi.isNotEmpty()) list.add("Pornografi (${detected.pornografi.size})")
        if (detected.judi.isNotEmpty()) list.add("Judi (${detected.judi.size})")
        if (detected.kasar.isNotEmpty()) list.add("Kata Kasar (${detected.kasar.size})")
        return list.joinToString(", ")
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            url
        }
    }
}