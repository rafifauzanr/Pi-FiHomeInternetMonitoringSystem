package com.example.cobacoba

import android.content.Intent
import android.net.Uri
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan

class LogsFragment : Fragment() {

    private lateinit var logsScrollView: ScrollView
    private lateinit var logsTextView: TextView
    private lateinit var summaryTextView: TextView

    private val TAG = "LogsFragment"
    private var shouldAutoRefresh = true
    private val WEB_LOGS_URL = "https://pifimonitoring.pagekite.me/web_activity_logs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logsScrollView = view.findViewById(R.id.logs_scroll_view)
        logsTextView = view.findViewById(R.id.logs_text_view)
        summaryTextView = view.findViewById(R.id.summary_text_view)

        startLogsFetching()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shouldAutoRefresh = false
    }

    private fun startLogsFetching() {
        lifecycleScope.launch {
            while (shouldAutoRefresh) {
                fetchWebActivityLogs()
                delay(3000)
            }
        }
    }

    private suspend fun fetchWebActivityLogs() {
        try {
            val response = RetrofitClient.apiService.getActivityLogs()
            if (response.isSuccessful) {
                response.body()?.let { logsResponse ->
                    activity?.runOnUiThread {
                        updateLogsDisplay(logsResponse)
                        logsScrollView.post {
                            logsScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to fetch logs: ${response.code()}")
                activity?.runOnUiThread {
                    summaryTextView.text = "Error: Failed to fetch logs (${response.code()})"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching logs: ${e.message}")
            activity?.runOnUiThread {
                summaryTextView.text = "Error: ${e.message}"
            }
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }

    private fun updateLogsDisplay(logsResponse: LogResponse) {
        val summary = logsResponse.summary
        val summaryText = """
            📊 Web Activity Summary
            ═══════════════════════════════════
            Total Logs: ${summary.total_logs_in_history}
            Displayed: ${summary.logs_returned}
            ✅ Safe Requests: ${summary.safe_requests}
            ⚠️ Unsafe Requests: ${summary.unsafe_requests}
            Last Updated: ${summary.last_updated}
        """.trimIndent()

        summaryTextView.text = summaryText

        val logsText = StringBuilder()

        val safeLogs = mutableListOf<WebActivityLog>()
        val unsafeLogs = mutableListOf<WebActivityLog>()

        logsResponse.logs.forEach { log ->
            if (log.is_safe) {
                safeLogs.add(log)
            } else {
                unsafeLogs.add(log)
            }
        }

        if (unsafeLogs.isNotEmpty()) {
            logsText.append("🚨 UNSAFE/BLOCKED ACTIVITIES\n")
            logsText.append("═".repeat(50)).append("\n\n")
            unsafeLogs.forEach { log ->
                logsText.append(formatLogEntry(log, false)).append("\n")
            }
        }

        if (safeLogs.isNotEmpty()) {
            logsText.append("\n✅ SAFE ACTIVITIES\n")
            logsText.append("═".repeat(50)).append("\n\n")
            safeLogs.take(20).forEach { log ->
                logsText.append(formatLogEntry(log, true)).append("\n")
            }
            if (safeLogs.size > 20) {
                logsText.append("... and ${safeLogs.size - 20} more safe activities\n")
                logsText.append("<a href=\"$WEB_LOGS_URL\">🔗 Klik untuk melihat logs secara lengkap</a>\n\n")
            }
        }

        logsTextView.text = HtmlCompat.fromHtml(logsText.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        logsTextView.movementMethod = LinkMovementMethod.getInstance()


    }

    private fun openWebLogs() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_LOGS_URL))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening web logs: ${e.message}")
            // Fallback: try to copy URL to clipboard or show toast
            activity?.let { context ->
                try {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Web Logs URL", WEB_LOGS_URL)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "URL copied to clipboard: $WEB_LOGS_URL", android.widget.Toast.LENGTH_LONG).show()
                } catch (clipboardError: Exception) {
                    android.widget.Toast.makeText(context, "Could not open browser. URL: $WEB_LOGS_URL", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun formatLogEntry(log: WebActivityLog, isSafe: Boolean): String {
        val statusIcon = if (isSafe) "✅" else "⚠️"
        val timestamp = formatTimestamp(log.timestamp)

        return if (isSafe) {
            """
            $statusIcon [$timestamp] ${log.device_ip}
            🌐 URL: <a href=\"${log.url}\">${log.url}</a>
            ───────────────────────────────────
            """.trimIndent()
        } else {
            """
            $statusIcon [$timestamp] ${log.device_ip}
            🌐 URL: <a href=\"${log.url}\">${extractDomain(log.url)}</a>
            🚫 Status: ${log.status}
            📁 Category: ${log.domain_category}
            📄 Content: ${log.content_status}
            ${if (log.detected_content.isNotEmpty()) "🔍 Detected: ${log.detected_content.joinToString(", ")}" else ""}
            ───────────────────────────────────
            """.trimIndent()
        }
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
}