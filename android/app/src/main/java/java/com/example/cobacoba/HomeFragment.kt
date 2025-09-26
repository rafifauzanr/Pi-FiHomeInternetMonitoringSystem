package com.example.cobacoba

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.Log
import java.text.DateFormat
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeFragment : Fragment() {

    private lateinit var totalDevicesTextView: TextView
    private lateinit var totalAlertsTextView: TextView
    private lateinit var connectionTimeTextView: TextView


    private val apiService: ApiService = RetrofitClient.apiService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val dateTextView = view.findViewById<TextView>(R.id.xml_text_date)
        val timeTextView = view.findViewById<TextView>(R.id.xml_text_time)


        totalDevicesTextView = view.findViewById<TextView>(R.id.devices_connected_text)
        totalAlertsTextView = view.findViewById<TextView>(R.id.total_alerts_text)
        connectionTimeTextView = view.findViewById<TextView>(R.id.connection_time_text)


        startClockUpdates(dateTextView, timeTextView)


        loadDashboardData()
    }

    private fun startClockUpdates(dateTextView: TextView, timeTextView: TextView) {
        lifecycleScope.launch {
            while (true) {
                val calendar = Calendar.getInstance()
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val dayName = when (dayOfWeek) {
                    Calendar.SUNDAY -> "Sunday"
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    else -> "Unknown"
                }

                val month = calendar.get(Calendar.MONTH)
                val monthName = when (month) {
                    Calendar.JANUARY -> "January"
                    Calendar.FEBRUARY -> "February"
                    Calendar.MARCH -> "March"
                    Calendar.APRIL -> "April"
                    Calendar.MAY -> "May"
                    Calendar.JUNE -> "June"
                    Calendar.JULY -> "July"
                    Calendar.AUGUST -> "August"
                    Calendar.SEPTEMBER -> "September"
                    Calendar.OCTOBER -> "October"
                    Calendar.NOVEMBER -> "November"
                    Calendar.DECEMBER -> "December"
                    else -> "Unknown"
                }

                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val year = calendar.get(Calendar.YEAR)

                val formattedDate = "Today is $dayName, $monthName $day, $year"
                val formattedTime = DateFormat.getTimeInstance().format(calendar.time)

                dateTextView.text = formattedDate
                timeTextView.text = formattedTime

                delay(1000)
            }
        }
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                Log.d("HomeFragment", "Starting to load dashboard data...")


                totalDevicesTextView.text = "Devices Connected: Loading..."
                totalAlertsTextView.text = "Total Alerts: Loading..."
                connectionTimeTextView.text = "Connection Time: Loading..."

                // Get dashboard data from API
                val response = apiService.getDashboardData()

                if (response.isSuccessful && response.body() != null) {
                    val dashboardData = response.body()!!


                    totalDevicesTextView.text = "Devices Connected: ${dashboardData.total_devices}"
                    totalAlertsTextView.text = "Total Alerts: ${dashboardData.total_alerts}"
                    connectionTimeTextView.text = "Connection Time: ${dashboardData.connection_time}"

                    Log.d("HomeFragment", "Dashboard data loaded: Devices=${dashboardData.total_devices}, Alerts=${dashboardData.total_alerts}, Time=${dashboardData.connection_time}")
                } else {
                    Log.e("HomeFragment", "API response failed: ${response.code()}")
                    throw Exception("API response failed")
                }

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error loading dashboard data", e)

                // Show fallback data dengan format yang benar (placeholder(
                totalDevicesTextView.text = "Devices Connected: 2"
                totalAlertsTextView.text = "Total Alerts: 3"
                connectionTimeTextView.text = "Connection Time: 01:15:30"

                // Optionally retry after delay
                lifecycleScope.launch {
                    delay(5000) // Wait 5 seconds
                    loadDashboardData() // Retry
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }
}