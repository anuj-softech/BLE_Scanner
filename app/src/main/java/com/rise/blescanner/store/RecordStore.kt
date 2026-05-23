package com.rise.blescanner.store

import android.content.Context
import android.content.SharedPreferences
import com.rise.blescanner.models.RecordingInfo
import org.json.JSONArray
import org.json.JSONObject

class RecordStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ble_scanner_recordings"
        private const val KEY_RECORDINGS = "recordings_list"
    }

    fun saveRecording(recording: RecordingInfo) {
        val list = getRecordings().toMutableList()
        list.add(recording)
        saveRecordingsList(list)
    }

    fun getRecordings(): List<RecordingInfo> {
        val jsonString = prefs.getString(KEY_RECORDINGS, null) ?: return emptyList()
        val list = mutableListOf<RecordingInfo>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RecordingInfo(
                        date = obj.getString("date"),
                        duration = obj.getString("duration"),
                        csvLocation = obj.getString("csvLocation"),
                        entryCount = obj.getInt("entryCount")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveRecordingsList(list: List<RecordingInfo>) {
        val jsonArray = JSONArray()
        for (info in list) {
            val obj = JSONObject().apply {
                put("date", info.date)
                put("duration", info.duration)
                put("csvLocation", info.csvLocation)
                put("entryCount", info.entryCount)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_RECORDINGS, jsonArray.toString()).apply()
    }
}
