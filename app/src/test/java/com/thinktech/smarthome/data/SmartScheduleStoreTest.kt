package com.thinktech.smarthome.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartScheduleStoreTest {

    @Test
    fun formatRepeatMask_handlesAllPresetsAndCustomDays() {
        assertEquals("Setiap Hari", SmartScheduleStore.formatRepeatMask("1111111"))
        assertEquals("Hari Kerja", SmartScheduleStore.formatRepeatMask("0111110"))
        assertEquals("Akhir Pekan", SmartScheduleStore.formatRepeatMask("1000001"))
        assertEquals("Sekali Saja", SmartScheduleStore.formatRepeatMask("0000000"))
        assertEquals("Sen, Rab, Jum", SmartScheduleStore.formatRepeatMask("0101010"))
    }

    @Test
    fun scheduleFormattedTimeAndActionSummary() {
        val s = SmartSchedule(
            id = "test_1",
            title = "Lampu Teras",
            hour = 21,
            minute = 5,
            repeatMask = "1111111",
            deviceId = "dev_abc",
            deviceName = "WiFi 3ch switch",
            channelIndex = 0,
            turnOn = true,
            enabled = true
        )
        assertEquals("21:05", s.formattedTime())
        assertEquals("Nyalakan • WiFi 3ch switch (L1)", s.actionSummary())
        assertEquals("Setiap Hari", s.repeatSummary())
    }

    @Test
    fun normalizeMask_padsShortOrInvalidMasksSafely() {
        assertEquals("1010000", SmartScheduleStore.normalizeMask("101"))
        assertEquals("1111111", SmartScheduleStore.normalizeMask("1111111000"))
    }
}
