package com.rise.blescanner.models

data class BleDeviceUiInfo(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val txPower: Int?
)