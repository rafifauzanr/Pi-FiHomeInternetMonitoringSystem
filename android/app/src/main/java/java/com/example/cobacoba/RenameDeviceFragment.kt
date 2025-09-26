package com.example.cobacoba

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class RenameDeviceFragment : Fragment() {
    private val apiBaseUrl = "https://pifimonitoring.pagekite.me"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Rename Devices"
        val view = inflater.inflate(R.layout.fragment_renamedevice, container, false)

        val containerLayout = view.findViewById<LinearLayout>(R.id.device_list_container)
        val loadingText = view.findViewById<TextView>(R.id.loading_text)

        fetchDevices { devices ->
            activity?.runOnUiThread {
                loadingText.visibility = View.GONE

                if (devices.isEmpty()) {
                    val emptyText = TextView(context).apply {
                        text = "No devices found"
                        textSize = 16f
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                        setPadding(0, 20, 0, 0)
                        gravity = Gravity.CENTER
                    }
                    containerLayout.addView(emptyText)
                    return@runOnUiThread
                }

                devices.forEach { (ip, currentName) ->
                    val deviceRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 16, 0, 16)
                    }

                    val ipText = TextView(context).apply {
                        text = ip
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setPadding(0, 0, 16, 0)
                    }

                    val nameEdit = EditText(context).apply {
                        hint = "Enter device name"
                        setText(currentName.orEmpty())
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                        setPadding(8, 8, 8, 8)
                    }

                    val saveBtn = Button(context).apply {
                        text = "Save"
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(16, 8, 16, 8)

                        setOnClickListener {
                            val newName = nameEdit.text.toString().trim()
                            if (newName.isNotEmpty()) {
                                RenameDeviceManager.saveDeviceName(requireContext(), ip, newName)
                                Toast.makeText(context, "Device name saved locally", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter a device name", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    deviceRow.addView(ipText)
                    deviceRow.addView(nameEdit)
                    deviceRow.addView(saveBtn)
                    containerLayout.addView(deviceRow)

                    // Add a divider
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            setMargins(0, 8, 0, 8)
                        }
                        setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                    }
                    containerLayout.addView(divider)
                }
            }
        }

        return view
    }

    private fun fetchDevices(callback: (Map<String, String?>) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$apiBaseUrl/connected_devices")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to fetch devices: ${e.message}", Toast.LENGTH_LONG).show()
                }
                callback(emptyMap())
            }

            override fun onResponse(call: Call, response: Response) {
                val map = mutableMapOf<String, String?>()
                try {
                    response.body()?.string()?.let { responseBody ->
                        try {
                            val result = JSONObject(responseBody)
                            val devicesJson = result.getJSONObject("devices")

                            devicesJson.keys().forEach { ip ->
                                val device = devicesJson.getJSONObject(ip)
                                val name = device.optString("device_name", "")
                                map[ip] = if (name.isNotBlank()) name else null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            activity?.runOnUiThread {
                                Toast.makeText(requireContext(), "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } finally {
                    response.close()
                }
                callback(map)
            }
        })
    }

    private fun saveDeviceName(ip: String, name: String, callback: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("ip", ip)
            put("name", name)
        }

        val mediaType = MediaType.get("application/json; charset=utf-8")
        val body = RequestBody.create(mediaType, json.toString())
        val request = Request.Builder()
            .url("$apiBaseUrl/rename_device")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val isSuccess = response.isSuccessful
                    if (!isSuccess) {
                        println("Server error: ${response.code()} ${response.message()}")
                        response.body()?.string()?.let {
                            println("Response body: $it")
                        }
                    }
                    callback(isSuccess)
                } finally {
                    response.close()
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
    }
}