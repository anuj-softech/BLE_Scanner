package com.rise.blescanner.store;

import android.content.Context
import android.content.SharedPreferences

data class BleDeviceConfig(
    val name: String,
    val macAddress: String
)

class ConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ble_scanner_config"
        private const val KEY_BLE1_NAME = "ble1_name"
        private const val KEY_BLE1_MAC = "ble1_mac"
        private const val KEY_BLE2_NAME = "ble2_name"
        private const val KEY_BLE2_MAC = "ble2_mac"
        private const val KEY_BLE3_NAME = "ble3_name"
        private const val KEY_BLE3_MAC = "ble3_mac"
    }

    fun saveBle1(config: BleDeviceConfig) {
        prefs.edit()
            .putString(KEY_BLE1_NAME, config.name)
            .putString(KEY_BLE1_MAC, config.macAddress)
            .apply()
    }

    fun getBle1(): BleDeviceConfig? {
        val name = prefs.getString(KEY_BLE1_NAME, null)
        val mac = prefs.getString(KEY_BLE1_MAC, null)
        return if (name != null && mac != null) BleDeviceConfig(name, mac) else null
    }

    fun saveBle2(config: BleDeviceConfig) {
        prefs.edit()
            .putString(KEY_BLE2_NAME, config.name)
            .putString(KEY_BLE2_MAC, config.macAddress)
            .apply()
    }

    fun getBle2(): BleDeviceConfig? {
        val name = prefs.getString(KEY_BLE2_NAME, null)
        val mac = prefs.getString(KEY_BLE2_MAC, null)
        return if (name != null && mac != null) BleDeviceConfig(name, mac) else null
    }

    fun saveBle3(config: BleDeviceConfig) {
        prefs.edit()
            .putString(KEY_BLE3_NAME, config.name)
            .putString(KEY_BLE3_MAC, config.macAddress)
            .apply()
    }

    fun getBle3(): BleDeviceConfig? {
        val name = prefs.getString(KEY_BLE3_NAME, null)
        val mac = prefs.getString(KEY_BLE3_MAC, null)
        return if (name != null && mac != null) BleDeviceConfig(name, mac) else null
    }
}