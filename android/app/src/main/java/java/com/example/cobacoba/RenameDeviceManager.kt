package com.example.cobacoba

import android.content.Context
import android.content.SharedPreferences

object RenameDeviceManager {
    private const val PREF_NAME = "DeviceNames"

    fun saveDeviceName(context: Context, ip: String, name: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ip, name).apply()
    }

    fun getDeviceName(context: Context, ip: String): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(ip, null)
    }
}
