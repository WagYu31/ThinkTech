package com.thinktech.smarthome.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val sampleDevices = listOf(
        VoiceCommandParser.DeviceRef(
            devId = "dev_switch_1",
            name = "WiFi 3ch switch",
            category = "Electrical",
            room = "Ruang Tamu",
            isCamera = false,
            channelNames = listOf("Lampu Teras", "Switch 2", "Switch 3")
        ),
        VoiceCommandParser.DeviceRef(
            devId = "dev_bulb_1",
            name = "Smart Bulb Bedroom RGB",
            category = "Lighting",
            room = "Kamar Tidur",
            isCamera = false
        ),
        VoiceCommandParser.DeviceRef(
            devId = "dev_cam_1",
            name = "Security Camera",
            category = "Kamera & Kunci",
            room = "Ruang Tamu",
            isCamera = true
        )
    )

    // ==================== SWITCH & ROOM & ALL CONTROLS ====================

    @Test
    fun parse_allDevicesOff_andOn() {
        assertEquals(VoiceCommandParser.Action.ToggleAll(false), VoiceCommandParser.parse("Matikan semua perangkat", sampleDevices))
        assertEquals(VoiceCommandParser.Action.ToggleAll(true), VoiceCommandParser.parse("Nyalakan semua lampu", sampleDevices))
    }

    @Test
    fun parse_specificSwitchChannelAndCustomChannelName() {
        val ch2 = VoiceCommandParser.parse("Nyalakan switch 2", sampleDevices)
        assertTrue(ch2 is VoiceCommandParser.Action.ToggleSwitchChannel)
        ch2 as VoiceCommandParser.Action.ToggleSwitchChannel
        assertEquals(1, ch2.channelIndex)
        assertEquals(true, ch2.turnOn)

        val custom = VoiceCommandParser.parse("Matikan lampu teras", sampleDevices)
        assertTrue(custom is VoiceCommandParser.Action.ToggleSwitchChannel)
        custom as VoiceCommandParser.Action.ToggleSwitchChannel
        assertEquals(0, custom.channelIndex)
        assertEquals(false, custom.turnOn)
    }

    @Test
    fun parse_roomLevelControl() {
        val action = VoiceCommandParser.parse("Matikan semua perangkat di ruang tamu", sampleDevices)
        assertEquals(VoiceCommandParser.Action.ToggleRoom("Ruang Tamu", false), action)
    }

    // ==================== FULL CAMERA COMMAND SUITE ====================

    @Test
    fun parse_cameraOpenAndTypoTolerance() {
        val open1 = VoiceCommandParser.parse("Buka kamara sekarang", sampleDevices)
        assertEquals(VoiceCommandParser.Action.OpenCamera("dev_cam_1", "Security Camera"), open1)

        val open2 = VoiceCommandParser.parse("Lihat CCTV", sampleDevices)
        assertEquals(VoiceCommandParser.Action.OpenCamera("dev_cam_1", "Security Camera"), open2)
    }

    @Test
    fun parse_cameraPtzDirections() {
        assertEquals(
            VoiceCommandParser.Action.CameraPtz("dev_cam_1", "Security Camera", VoiceCommandParser.PtzDirection.LEFT),
            VoiceCommandParser.parse("Geser kamera ke kiri", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPtz("dev_cam_1", "Security Camera", VoiceCommandParser.PtzDirection.RIGHT),
            VoiceCommandParser.parse("Putar kamera ke kanan", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPtz("dev_cam_1", "Security Camera", VoiceCommandParser.PtzDirection.UP),
            VoiceCommandParser.parse("Naikkan kamera ke atas", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPtz("dev_cam_1", "Security Camera", VoiceCommandParser.PtzDirection.DOWN),
            VoiceCommandParser.parse("Turunkan kamera ke bawah", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPtz("dev_cam_1", "Security Camera", VoiceCommandParser.PtzDirection.STOP),
            VoiceCommandParser.parse("Hentikan kamera", sampleDevices)
        )
    }

    @Test
    fun parse_cameraMotionTrackingNightVisionPrivacyFlipSiren() {
        assertEquals(
            VoiceCommandParser.Action.CameraMotionDetection("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Aktifkan deteksi gerakan kamera", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraMotionDetection("dev_cam_1", "Security Camera", false),
            VoiceCommandParser.parse("Matikan motion alert", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraAutoTracking("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Nyalakan pelacakan otomatis", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraNightVision("dev_cam_1", "Security Camera", "2", "Aktif"),
            VoiceCommandParser.parse("Aktifkan mode malam inframerah", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraNightVision("dev_cam_1", "Security Camera", "0", "Otomatis"),
            VoiceCommandParser.parse("Mode malam kamera otomatis", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPrivacyMode("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Aktifkan mode privasi kamera", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraPrivacyMode("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Matikan kamera", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraFlipScreen("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Balik layar kamera 180 derajat", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraSiren("dev_cam_1", "Security Camera", true),
            VoiceCommandParser.parse("Bunyikan sirene kamera", sampleDevices)
        )
    }

    @Test
    fun parse_cameraLiveActions_snapshotRecordTalkAudio() {
        assertEquals(
            VoiceCommandParser.Action.CameraLiveAction("dev_cam_1", "Security Camera", VoiceCommandParser.CameraLiveActionType.SNAPSHOT),
            VoiceCommandParser.parse("Ambil foto kamera sekarang", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraLiveAction("dev_cam_1", "Security Camera", VoiceCommandParser.CameraLiveActionType.RECORD),
            VoiceCommandParser.parse("Rekam video CCTV", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraLiveAction("dev_cam_1", "Security Camera", VoiceCommandParser.CameraLiveActionType.TALK),
            VoiceCommandParser.parse("Bicara lewat kamera", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.CameraLiveAction("dev_cam_1", "Security Camera", VoiceCommandParser.CameraLiveActionType.MUTE),
            VoiceCommandParser.parse("Bisukan suara kamera", sampleDevices)
        )
    }

    // ==================== LIGHTING, SCHEDULE, LANGUAGE, SCENES, HELP ====================

    @Test
    fun parse_lightingBrightnessAndColor() {
        val bright = VoiceCommandParser.parse("Atur kecerahan lampu 60 persen", sampleDevices)
        assertEquals(VoiceCommandParser.Action.SetLightBrightness("dev_bulb_1", "Smart Bulb Bedroom RGB", 60), bright)

        val dim = VoiceCommandParser.parse("Redupkan lampu", sampleDevices)
        assertEquals(VoiceCommandParser.Action.SetLightBrightness("dev_bulb_1", "Smart Bulb Bedroom RGB", 25), dim)

        val color = VoiceCommandParser.parse("Ubah lampu jadi warna biru", sampleDevices)
        assertEquals(VoiceCommandParser.Action.SetLightColor("dev_bulb_1", "Smart Bulb Bedroom RGB", "Biru", 232f, null), color)
    }

    @Test
    fun parse_scheduleLanguageScenesAndHelp() {
        assertEquals(
            VoiceCommandParser.Action.OpenSchedule(21, 30, false),
            VoiceCommandParser.parse("Buat jadwal jam 21:30 matikan lampu", sampleDevices)
        )
        assertEquals(
            VoiceCommandParser.Action.ChangeLanguage("en"),
            VoiceCommandParser.parse("Ubah ke bahasa Inggris", sampleDevices)
        )
        assertEquals(VoiceCommandParser.Action.RunSleepScene, VoiceCommandParser.parse("Jalankan mode tidur", sampleDevices))
        assertEquals(VoiceCommandParser.Action.RunLeaveScene, VoiceCommandParser.parse("Tinggalkan rumah sekarang", sampleDevices))
        assertEquals(VoiceCommandParser.Action.CheckStatus, VoiceCommandParser.parse("Cek status rumah", sampleDevices))
        assertEquals(VoiceCommandParser.Action.ShowCapabilities, VoiceCommandParser.parse("Kamu bisa apa saja", sampleDevices))
    }

    @Test
    fun parse_unknownReturnsUnknown() {
        val action = VoiceCommandParser.parse("Siapa presiden mars", sampleDevices)
        assertTrue(action is VoiceCommandParser.Action.Unknown)
    }
}
