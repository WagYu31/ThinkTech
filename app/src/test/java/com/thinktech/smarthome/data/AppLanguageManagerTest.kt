package com.thinktech.smarthome.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageManagerTest {

    @Test
    fun translate_handlesCoreScreensAcrossAllFiveLanguages() {
        assertEquals("My Home", AppLanguageManager.translate("Rumah Saya", "en"))
        assertEquals("我的家", AppLanguageManager.translate("Rumah Saya", "zh"))
        assertEquals("マイホーム", AppLanguageManager.translate("Rumah Saya", "ja"))
        assertEquals("App Language", AppLanguageManager.translate("Bahasa Aplikasi", "en"))
        assertEquals("Schedule Time", AppLanguageManager.translate("Atur Waktu Jadwal", "en"))
        assertEquals("We are listening...", AppLanguageManager.translate("Kami mendengarkan...", "en"))
    }

    @Test
    fun translate_handlesDynamicDeviceAndScheduleCounters() {
        assertEquals("Devices (3)", AppLanguageManager.translate("Perangkat (3)", "en"))
        assertEquals("2 Active Devices", AppLanguageManager.translate("2 Perangkat Aktif", "en"))
        assertEquals("6 instant scenes • 2 active schedules", AppLanguageManager.translate("6 pintasan instan • 2 jadwal aktif", "en"))
        assertEquals("Demo Mode • Color #334EFF • 85%", AppLanguageManager.translate("Mode Demo • Warna #334EFF • 85%", "en"))
    }

    @Test
    fun getLanguageByCode_fallsBackSafelyToIndonesian() {
        assertEquals("en-US", AppLanguageManager.getLanguageByCode("en").speechTag)
        assertEquals("ja-JP", AppLanguageManager.getLanguageByCode("ja").speechTag)
        assertEquals("id", AppLanguageManager.getLanguageByCode("unknown").code)
    }
}
