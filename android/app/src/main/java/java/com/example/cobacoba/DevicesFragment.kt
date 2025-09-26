package com.example.cobacoba

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private lateinit var devicesScrollView: ScrollView
    private lateinit var devicesTextView: TextView
    private val TAG = "DevicesFragment"
    private var shouldAutoRefresh = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        devicesScrollView = view.findViewById(R.id.devices_scroll_view)
        devicesTextView = view.findViewById(R.id.devices_text_view)
        startDevicesFetching()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shouldAutoRefresh = false
    }

    private fun startDevicesFetching() {
        lifecycleScope.launch {
            while (shouldAutoRefresh) {
                fetchDevices()
                delay(3000)
            }
        }
    }

    private suspend fun fetchDevices() {
        try {
            val response = RetrofitClient.apiService.getConnectedDevices()
            if (response.isSuccessful) {
                response.body()?.let { devicesResponse ->
                    activity?.runOnUiThread {
                        updateDevicesDisplay(devicesResponse)
                        devicesScrollView.post {
                            devicesScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to fetch devices: ${response.code()}")
                activity?.runOnUiThread {
                    devicesTextView.text = "Error: Failed to fetch devices (${response.code()})"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching devices: ${e.message}")
            activity?.runOnUiThread {
                devicesTextView.text = "Error: ${e.message}"
            }
        }
    }

    private fun updateDevicesDisplay(response: ConnectedDeviceResponse) {
        val builder = StringBuilder()

        builder.append("📡 Total Devices Connected: ${response.total_devices}\n")
        builder.append("═".repeat(50)).append("\n\n")

        response.devices.forEach { (ip, detail) ->
            val name = RenameDeviceManager.getDeviceName(requireContext(), ip)
            val displayName = name?.let { "$it ($ip)" } ?: ip
            builder.append("🖥️ Device: $displayName\n")
            builder.append("🕓 First Connected: ${detail.first_connected}\n")
            builder.append("🕘 Last Connected: ${detail.last_connected}\n")
            builder.append("⏳ Duration: ${detail.connection_duration_formatted}\n")
            builder.append("📊 Requests: ${detail.request_count}\n")
            builder.append("🌐 Domains Visited (${detail.domains_count}): ${detail.domains_visited.joinToString(", ")}\n")
            builder.append("⚙️ Status: ${detail.status}\n")
            builder.append("─".repeat(50)).append("\n\n")
        }

        if (response.total_devices == 0) {
            builder.append("✅ No devices are currently connected.")
        }

        devicesTextView.text = builder.toString()
    }
}
