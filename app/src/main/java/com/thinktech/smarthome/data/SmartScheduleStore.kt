package com.thinktech.smarthome.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Unified Smart Schedule model shared across Tab Smart (Otomasi Cloud) and DeviceControlActivity.
 * repeatMask uses 7 chars "0"/"1" from Sunday(0) to Saturday(6), matching Tuya Cloud Timer loop format.
 */
data class SmartSchedule(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeatMask: String = "1111111",
    val deviceId: String = DEVICE_ALL,
    val deviceName: String = "Semua Saklar",
    val channelIndex: Int = -1, // -1 = all channels / primary power, 0..2 = Switch 1..3
    val turnOn: Boolean = true,
    val enabled: Boolean = true
) {
    fun formattedTime(): String = String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun repeatSummary(): String = SmartScheduleStore.formatRepeatMask(repeatMask)

    fun actionSummary(): String {
        val verb = if (turnOn) "Nyalakan" else "Matikan"
        val chSuffix = if (channelIndex in 0..2) " (L${channelIndex + 1})" else ""
        return "$verb • $deviceName$chSuffix"
    }

    companion object {
        const val DEVICE_ALL = "all"
    }
}

object SmartScheduleStore {

    private const val PREF_NAME = "thinktech_smart_schedules_v1"
    private const val KEY_SCHEDULES_JSON = "schedules_json"
    private const val KEY_SEEDED = "seeded_defaults_v1"

    private val DAY_LABELS = arrayOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    fun normalizeMask(mask: String): String {
        val clean = mask.filter { it == '0' || it == '1' }
        return when {
            clean.length >= 7 -> clean.substring(0, 7)
            else -> clean.padEnd(7, '0')
        }
    }

    fun formatRepeatMask(rawMask: String): String {
        val mask = normalizeMask(rawMask)
        return when (mask) {
            "1111111" -> "Setiap Hari"
            "0111110" -> "Hari Kerja"
            "1000001" -> "Akhir Pekan"
            "0000000" -> "Sekali Saja"
            else -> {
                val active = mutableListOf<String>()
                for (i in 0 until 7) {
                    if (mask[i] == '1') active.add(DAY_LABELS[i])
                }
                active.joinToString(", ")
            }
        }
    }

    fun toJson(list: List<SmartSchedule>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("hour", s.hour)
                put("minute", s.minute)
                put("repeatMask", normalizeMask(s.repeatMask))
                put("deviceId", s.deviceId)
                put("deviceName", s.deviceName)
                put("channelIndex", s.channelIndex)
                put("turnOn", s.turnOn)
                put("enabled", s.enabled)
            })
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<SmartSchedule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<SmartSchedule>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                result.add(
                    SmartSchedule(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", "Jadwal Otomatis"),
                        hour = o.optInt("hour", 18).coerceIn(0, 23),
                        minute = o.optInt("minute", 0).coerceIn(0, 59),
                        repeatMask = normalizeMask(o.optString("repeatMask", "1111111")),
                        deviceId = o.optString("deviceId", SmartSchedule.DEVICE_ALL),
                        deviceName = o.optString("deviceName", "Semua Saklar"),
                        channelIndex = o.optInt("channelIndex", -1),
                        turnOn = o.optBoolean("turnOn", true),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
            result.sortedWith(compareBy<SmartSchedule> { it.hour }.thenBy { it.minute })
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun loadAll(context: Context): List<SmartSchedule> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            val defaults = listOf(
                SmartSchedule(
                    id = "seed_evening_on",
                    title = "Nyalakan Lampu Sore",
                    hour = 18,
                    minute = 0,
                    repeatMask = "1111111",
                    deviceId = SmartSchedule.DEVICE_ALL,
                    deviceName = "Semua Saklar",
                    channelIndex = -1,
                    turnOn = true,
                    enabled = true
                ),
                SmartSchedule(
                    id = "seed_night_off",
                    title = "Jadwal Malam Otomatis",
                    hour = 22,
                    minute = 30,
                    repeatMask = "1111111",
                    deviceId = SmartSchedule.DEVICE_ALL,
                    deviceName = "Semua Saklar",
                    channelIndex = -1,
                    turnOn = false,
                    enabled = true
                )
            )
            prefs.edit()
                .putString(KEY_SCHEDULES_JSON, toJson(defaults))
                .putBoolean(KEY_SEEDED, true)
                .apply()
            return defaults
        }
        return fromJson(prefs.getString(KEY_SCHEDULES_JSON, null))
    }

    fun loadForDevice(context: Context, devId: String): List<SmartSchedule> {
        return loadAll(context).filter { it.deviceId == devId || it.deviceId == SmartSchedule.DEVICE_ALL }
    }

    fun findById(context: Context, id: String): SmartSchedule? {
        return loadAll(context).firstOrNull { it.id == id }
    }

    fun upsert(context: Context, schedule: SmartSchedule): List<SmartSchedule> {
        val current = loadAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) {
            current[idx] = schedule
        } else {
            current.add(schedule)
        }
        val sorted = current.sortedWith(compareBy<SmartSchedule> { it.hour }.thenBy { it.minute })
        saveList(context, sorted)
        return sorted
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean): List<SmartSchedule> {
        val updated = loadAll(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        saveList(context, updated)
        return updated
    }

    fun delete(context: Context, id: String): List<SmartSchedule> {
        val updated = loadAll(context).filterNot { it.id == id }
        saveList(context, updated)
        return updated
    }

    private fun saveList(context: Context, list: List<SmartSchedule>) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SCHEDULES_JSON, toJson(list))
            .putBoolean(KEY_SEEDED, true)
            .apply()
    }
}
