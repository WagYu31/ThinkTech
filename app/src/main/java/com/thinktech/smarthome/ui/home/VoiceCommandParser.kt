package com.thinktech.smarthome.ui.home

/**
 * Rich Natural-Language Voice Command Parser for Indonesian & English Smart Home commands.
 * Covers full Security Camera controls (PTZ pan/tilt, motion alert, auto-tracking, IR night vision,
 * privacy lens, flip 180°, siren alarm, snapshot, record, 2-way talk, audio mute/unmute),
 * multi-gang switches, smart bulb color & brightness, room-level control, schedules, language,
 * energy audit, scenes, status queries, and capability help.
 */
object VoiceCommandParser {

    data class DeviceRef(
        val devId: String,
        val name: String,
        val category: String,
        val room: String,
        val isCamera: Boolean,
        val channelNames: List<String> = emptyList()
    )

    enum class PtzDirection { UP, DOWN, LEFT, RIGHT, STOP }

    enum class CameraLiveActionType { OPEN, SNAPSHOT, RECORD, TALK, UNMUTE, MUTE }

    sealed class Action {
        // Power & Switch Controls
        data class ToggleAll(val turnOn: Boolean) : Action()
        data class ToggleRoom(val roomName: String, val turnOn: Boolean) : Action()
        data class ToggleDevice(val devId: String, val deviceName: String, val turnOn: Boolean) : Action()
        data class ToggleSwitchChannel(
            val devId: String,
            val deviceName: String,
            val channelIndex: Int,
            val channelLabel: String,
            val turnOn: Boolean
        ) : Action()

        // Full Security Camera Suite
        data class OpenCamera(val devId: String, val deviceName: String) : Action()
        data class CameraLiveAction(
            val devId: String,
            val deviceName: String,
            val type: CameraLiveActionType
        ) : Action()
        data class CameraPtz(
            val devId: String,
            val deviceName: String,
            val direction: PtzDirection
        ) : Action()
        data class CameraMotionDetection(
            val devId: String,
            val deviceName: String,
            val enabled: Boolean
        ) : Action()
        data class CameraAutoTracking(
            val devId: String,
            val deviceName: String,
            val enabled: Boolean
        ) : Action()
        data class CameraNightVision(
            val devId: String,
            val deviceName: String,
            val modeCode: String, // "0" Auto, "1" Off, "2" On
            val modeLabel: String
        ) : Action()
        data class CameraPrivacyMode(
            val devId: String,
            val deviceName: String,
            val enabled: Boolean
        ) : Action()
        data class CameraFlipScreen(
            val devId: String,
            val deviceName: String,
            val enabled: Boolean
        ) : Action()
        data class CameraSiren(
            val devId: String,
            val deviceName: String,
            val enabled: Boolean
        ) : Action()

        // Smart Lighting Controls (Brightness & Color/CCT)
        data class SetLightBrightness(
            val devId: String,
            val deviceName: String,
            val percent: Int
        ) : Action()
        data class SetLightColor(
            val devId: String,
            val deviceName: String,
            val colorLabel: String,
            val hue: Float?,       // Non-null for RGB
            val cctProgress: Int?  // Non-null for White CCT (0..100)
        ) : Action()

        // Smart Scenes & App Workflows
        object RunSleepScene : Action()
        object RunLeaveScene : Action()
        object RunHomeScene : Action()
        object RunEnergyAudit : Action()
        object CheckStatus : Action()
        object OpenAddDevice : Action()
        data class OpenSchedule(val hour: Int? = null, val minute: Int? = null, val turnOn: Boolean? = null) : Action()
        data class ChangeLanguage(val langCode: String? = null) : Action()
        object OpenRoomManagement : Action()
        object ShowCapabilities : Action()
        data class Unknown(val spokenText: String) : Action()
    }

    private val onRegex = Regex(
        "\\b(?:nyalakan|nyala|hidupkan|hidup|aktifkan|aktif|bunyikan|mulai|on|turn on|switch on|enable|start|activate|sound|buka daya|nyalain|idupin|aktifin)\\b"
    )

    private val offRegex = Regex(
        "\\b(?:matikan|mati|nonaktifkan|nonaktif|padamkan|hentikan|berhenti|stop|tutup|off|turn off|switch off|disable|mute|bisukan|senyapkan|matiin|padamin|nonaktifin)\\b"
    )

    private val cameraKeywords = listOf(
        "kamera", "kamara", "camera", "cctv", "lensa", "ptz", "pantau", "video live", "live view"
    )

    fun parse(rawText: String, devices: List<DeviceRef>): Action {
        val text = rawText.lowercase().trim()
        if (text.isEmpty()) return Action.Unknown(rawText)

        // 0. Help / Capabilities inquiry
        if (listOf(
                "bisa apa saja", "apa saja perintah", "daftar perintah", "bantuan suara",
                "kamu bisa apa", "fitur apa saja", "what can you do", "voice commands", "help"
            ).any { text.contains(it) }
        ) {
            return Action.ShowCapabilities
        }

        val cam = devices.firstOrNull { it.isCamera }
        val nonCamDevices = devices.filter { !it.isCamera }
        val mentionsCamera = cameraKeywords.any { text.contains(it) }

        // ==================== 1. FULL SECURITY CAMERA COMMAND SUITE ====================
        if (cam != null) {
            // 1A. Camera Siren / Alarm
            if (listOf("sirene", "sirena", "sirine", "alarm", "peringatan bunyi", "siren").any { text.contains(it) }) {
                val turnOn = !hasOffIntent(text)
                return Action.CameraSiren(cam.devId, cam.name, turnOn)
            }

            // 1B. Camera Auto-Tracking / Pelacakan Objek
            if (listOf("pelacakan", "pelacak", "lacak", "tracking", "auto track", "ikuti gerakan", "ikuti objek", "follow motion").any { text.contains(it) }) {
                val enable = !hasOffIntent(text)
                return Action.CameraAutoTracking(cam.devId, cam.name, enable)
            }

            // 1C. Camera Motion Detection / Deteksi Gerakan
            if (listOf("deteksi gerakan", "deteksi gerak", "sensor gerak", "motion alert", "motion detection", "peringatan gerakan").any { text.contains(it) }) {
                val enable = !hasOffIntent(text)
                return Action.CameraMotionDetection(cam.devId, cam.name, enable)
            }

            // 1D. Camera Infrared / Night Vision Mode
            if (listOf("mode malam", "inframerah", "infra merah", "night vision", "nightvision", "penglihatan malam", "ir kamera", "ir cctv").any { text.contains(it) }) {
                return when {
                    listOf("otomatis", "auto").any { text.contains(it) } ->
                        Action.CameraNightVision(cam.devId, cam.name, "0", "Otomatis")
                    hasOffIntent(text) ->
                        Action.CameraNightVision(cam.devId, cam.name, "1", "Mati")
                    else ->
                        Action.CameraNightVision(cam.devId, cam.name, "2", "Aktif")
                }
            }

            // 1E. Camera Privacy Mode / Close Lens / Sleep Camera
            val wantsPrivacy = listOf("mode privasi", "privasi", "privacy", "tutup lensa", "buka lensa", "sembunyikan lensa").any { text.contains(it) }
            if (wantsPrivacy) {
                val enablePrivacy = if (text.contains("buka lensa")) false else !hasOffIntent(text) || text.contains("tutup lensa")
                return Action.CameraPrivacyMode(cam.devId, cam.name, enablePrivacy)
            }

            // 1F. Camera Flip Screen 180°
            if (listOf("putar layar", "balik layar", "balik gambar", "180 derajat", "flip layar", "flip kamera", "flip camera", "rotate screen").any { text.contains(it) }) {
                val enable = !hasOffIntent(text)
                return Action.CameraFlipScreen(cam.devId, cam.name, enable)
            }

            // 1G. Camera PTZ Pan / Tilt Direction
            val ptzDir = extractPtzDirection(text, mentionsCamera)
            if (ptzDir != null) {
                return Action.CameraPtz(cam.devId, cam.name, ptzDir)
            }

            // 1H. Camera Snapshot / Potret
            if (listOf("ambil foto", "potret", "foto kamera", "foto cctv", "screenshot kamera", "jepret", "snapshot", "take photo", "take picture").any { text.contains(it) }) {
                return Action.CameraLiveAction(cam.devId, cam.name, CameraLiveActionType.SNAPSHOT)
            }

            // 1I. Camera Record Video
            if (listOf("rekam video", "rekam kamera", "rekam cctv", "mulai rekam", "mulai rekaman", "record video", "start recording").any { text.contains(it) }) {
                return Action.CameraLiveAction(cam.devId, cam.name, CameraLiveActionType.RECORD)
            }

            // 1J. Camera Two-Way Talk / Intercom
            if (listOf("bicara lewat kamera", "bicara ke kamera", "ngomong ke kamera", "interkom", "intercom", "two way talk", "talk to camera", "bicara dua arah").any { text.contains(it) }) {
                return Action.CameraLiveAction(cam.devId, cam.name, CameraLiveActionType.TALK)
            }

            // 1K. Camera Audio Mute / Unmute
            if (mentionsCamera && listOf("suara", "audio", "bisukan", "mute", "dengar", "speaker", "unmute").any { text.contains(it) }) {
                val mute = hasOffIntent(text) || text.contains("bisu") || text.contains("senyap") || text.contains("mute") && !text.contains("unmute")
                return Action.CameraLiveAction(
                    cam.devId,
                    cam.name,
                    if (mute) CameraLiveActionType.MUTE else CameraLiveActionType.UNMUTE
                )
            }

            // 1L. Explicit "Matikan kamera" -> Privacy Mode ON; "Nyalakan kamera" -> Privacy Mode OFF / Open
            if (mentionsCamera && hasOffIntent(text) && !listOf("semua", "seluruh", "saklar", "lampu").any { text.contains(it) }) {
                return Action.CameraPrivacyMode(cam.devId, cam.name, true)
            }

            // 1M. Open Camera / Live CCTV
            val wantsOpen = listOf("buka", "lihat", "tampilkan", "cek", "pantau", "open", "show", "watch", "live", "nyalakan", "hidupkan").any { text.contains(it) }
            if (mentionsCamera && (wantsOpen || (!hasOnIntent(text) && !hasOffIntent(text)))) {
                return Action.OpenCamera(cam.devId, cam.name)
            }
        }

        // ==================== 2. SCHEDULE, LANGUAGE, ROOM MGMT, ADD DEVICE ====================
        if (listOf("buat jadwal", "tambah jadwal", "atur jadwal", "jadwalkan", "create schedule", "set schedule", "schedule time").any { text.contains(it) }) {
            val (h, m) = extractTimeFromText(text)
            val actionOn = when {
                hasOffIntent(text) -> false
                hasOnIntent(text) -> true
                else -> null
            }
            return Action.OpenSchedule(h, m, actionOn)
        }

        if (listOf("ganti bahasa", "ubah bahasa", "bahasa aplikasi", "app language", "change language", "switch to english", "bahasa inggris", "bahasa indonesia", "bahasa jepang", "bahasa mandarin", "bahasa melayu").any { text.contains(it) }) {
            val targetLang = when {
                listOf("inggris", "english").any { text.contains(it) } -> "en"
                listOf("indonesia").any { text.contains(it) } -> "id"
                listOf("melayu", "malay").any { text.contains(it) } -> "ms"
                listOf("mandarin", "china", "cina", "chinese").any { text.contains(it) } -> "zh"
                listOf("jepang", "japanese").any { text.contains(it) } -> "ja"
                else -> null
            }
            return Action.ChangeLanguage(targetLang)
        }

        if (listOf("kelola ruangan", "atur ruangan", "tambah ruangan", "manajemen ruangan", "manage room", "add room").any { text.contains(it) }) {
            return Action.OpenRoomManagement
        }

        if (listOf("tambah perangkat", "add device", "pairing", "hubungkan perangkat baru").any { text.contains(it) }) {
            return Action.OpenAddDevice
        }

        if (listOf("hemat energi", "audit energi", "audit daya", "energy saver").any { text.contains(it) }) {
            return Action.RunEnergyAudit
        }

        // ==================== 3. STATUS & SMART SCENES ====================
        if (listOf("cek status", "status rumah", "status perangkat", "berapa perangkat", "cek perangkat", "home status").any { text.contains(it) }) {
            return Action.CheckStatus
        }

        if (listOf("mode tidur", "selamat malam", "mau tidur", "waktunya tidur", "persiapan tidur", "sleep mode", "good night", "bedtime").any { text.contains(it) }) {
            return Action.RunSleepScene
        }
        if (listOf("tinggalkan rumah", "keluar rumah", "mode keluar", "pergi keluar", "leave home", "away mode").any { text.contains(it) }) {
            return Action.RunLeaveScene
        }
        if (listOf("mode pulang", "saya pulang", "pulang ke rumah", "welcome home", "home mode", "arrive home").any { text.contains(it) }) {
            return Action.RunHomeScene
        }

        // ==================== 4. SMART LIGHTING BRIGHTNESS & COLOR ====================
        val lightDev = findLightingDevice(nonCamDevices)
        if (lightDev != null) {
            val brightnessPct = extractBrightnessPercent(text)
            if (brightnessPct != null) {
                return Action.SetLightBrightness(lightDev.devId, lightDev.name, brightnessPct)
            }
            val colorMatch = extractLightColor(text)
            if (colorMatch != null) {
                return Action.SetLightColor(
                    devId = lightDev.devId,
                    deviceName = lightDev.name,
                    colorLabel = colorMatch.first,
                    hue = colorMatch.second,
                    cctProgress = colorMatch.third
                )
            }
        }

        // ==================== 5. POWER ON / OFF CONTROLS ====================
        val isOff = hasOffIntent(text)
        val isOn = hasOnIntent(text) && !isOff
        if (!isOn && !isOff) {
            return Action.Unknown(rawText)
        }
        val targetState = isOn

        // 5A. Specific Room ON / OFF (e.g. "matikan semua di kamar tidur", "nyalakan ruang tamu")
        val roomMatch = extractRoomTarget(text, nonCamDevices)
        if (roomMatch != null && !listOf("switch 1", "switch 2", "switch 3", "saklar 1", "saklar 2", "saklar 3").any { text.contains(it) }) {
            return Action.ToggleRoom(roomMatch, targetState)
        }

        // 5B. All devices ON / OFF
        val mentionsAll = listOf("semua", "seluruh", "all", "seisi rumah").any { text.contains(it) }
        if (mentionsAll) {
            return Action.ToggleAll(targetState)
        }

        // 5C. Specific Switch Channel (1 / 2 / 3 or custom channel name)
        val channelIdx = extractChannelIndex(text)
        if (channelIdx != null) {
            val switchDev = nonCamDevices.firstOrNull { dev ->
                val n = dev.name.lowercase()
                val c = dev.category.lowercase()
                n.contains("switch") || n.contains("saklar") || n.contains("3ch") || n.contains("2ch") || c.contains("electrical")
            } ?: nonCamDevices.firstOrNull()

            if (switchDev != null) {
                val label = switchDev.channelNames.getOrNull(channelIdx)?.takeIf { it.isNotBlank() }
                    ?: "Switch ${channelIdx + 1}"
                return Action.ToggleSwitchChannel(
                    devId = switchDev.devId,
                    deviceName = switchDev.name,
                    channelIndex = channelIdx,
                    channelLabel = label,
                    turnOn = targetState
                )
            }
        }

        for (dev in nonCamDevices) {
            dev.channelNames.forEachIndexed { idx, chName ->
                val clean = chName.lowercase().trim()
                if (clean.length >= 3 && !clean.startsWith("switch ") && text.contains(clean)) {
                    return Action.ToggleSwitchChannel(
                        devId = dev.devId,
                        deviceName = dev.name,
                        channelIndex = idx,
                        channelLabel = chName,
                        turnOn = targetState
                    )
                }
            }
        }

        // 5D. Specific Device Match
        val matchedDevice = findBestDeviceMatch(text, nonCamDevices)
        if (matchedDevice != null) {
            return Action.ToggleDevice(
                devId = matchedDevice.devId,
                deviceName = matchedDevice.name,
                turnOn = targetState
            )
        }

        // 5E. Generic fallback for single or multiple controllable devices
        if (nonCamDevices.size == 1 && listOf("lampu", "saklar", "switch", "perangkat", "listrik", "alat").any { text.contains(it) }) {
            val only = nonCamDevices.first()
            return Action.ToggleDevice(only.devId, only.name, targetState)
        }

        if (listOf("lampu", "saklar", "switch", "perangkat").any { text.contains(it) } && nonCamDevices.isNotEmpty()) {
            return Action.ToggleAll(targetState)
        }

        return Action.Unknown(rawText)
    }

    private fun extractPtzDirection(text: String, mentionsCamera: Boolean): PtzDirection? {
        val hasMoveVerb = listOf(
            "geser", "putar", "puter", "arahkan", "gerakkan", "tengok", "naikkan", "turunkan",
            "pan", "tilt", "move", "turn", "look", "ptz", "henti", "stop"
        ).any { text.contains(it) }

        if (!mentionsCamera && !hasMoveVerb) return null

        return when {
            listOf("berhenti", "hentikan", "stop ptz", "stop gerak", "stop kamera").any { text.contains(it) } && (mentionsCamera || text.contains("ptz")) ->
                PtzDirection.STOP
            listOf("ke atas", "atas", "naikkan", "naik", " up").any { text.contains(it) } && (mentionsCamera || hasMoveVerb) ->
                PtzDirection.UP
            listOf("ke bawah", "bawah", "turunkan", "turun", " down").any { text.contains(it) } && (mentionsCamera || hasMoveVerb) ->
                PtzDirection.DOWN
            listOf("ke kiri", "kiri", " left").any { text.contains(it) } && (mentionsCamera || hasMoveVerb) ->
                PtzDirection.LEFT
            listOf("ke kanan", "kanan", " right").any { text.contains(it) } && (mentionsCamera || hasMoveVerb) ->
                PtzDirection.RIGHT
            else -> null
        }
    }

    private fun extractTimeFromText(text: String): Pair<Int?, Int?> {
        // Match "21:30" or "21.30" or "jam 9 malam" or "jam 6 pagi"
        Regex("\\b(\\d{1,2})[:.](\\d{2})\\b").find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23)
            val min = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59)
            return h to min
        }
        Regex("\\b(?:jam|pukul|at)\\s+(\\d{1,2})\\b").find(text)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return null to null
            if (listOf("malam", "sore", "pm").any { text.contains(it) } && h in 1..11) {
                h += 12
            }
            return h.coerceIn(0, 23) to 0
        }
        return null to null
    }

    private fun extractBrightnessPercent(text: String): Int? {
        val mentionsBrightness = listOf("kecerahan", "terang", "redup", "dim", "brightness", "persen", "%").any { text.contains(it) }
        if (!mentionsBrightness) return null

        Regex("\\b(\\d{1,3})\\s*(?:%|persen|percent)?\\b").find(text)?.let { m ->
            val num = m.groupValues[1].toIntOrNull()
            if (num != null && num in 1..100 && (text.contains("kecerahan") || text.contains("brightness") || text.contains("%") || text.contains("persen"))) {
                return num.coerceIn(5, 100)
            }
        }
        return when {
            listOf("redupkan", "redupin", "redup", "dim light", "dimmer").any { text.contains(it) } -> 25
            listOf("terangkan", "terangin", "paling terang", "maksimal", "brighten", "full brightness").any { text.contains(it) } -> 100
            else -> null
        }
    }

    private fun extractLightColor(text: String): Triple<String, Float?, Int?>? {
        val mentionsLightOrColor = listOf("lampu", "bohlam", "bulb", "warna", "color", "light", "cahaya").any { text.contains(it) }
        if (!mentionsLightOrColor) return null

        return when {
            listOf("putih hangat", "warm white", "kuning hangat", "lilin").any { text.contains(it) } ->
                Triple("Putih Hangat", null, 15)
            listOf("putih sejuk", "cool white", "putih siang", "daylight").any { text.contains(it) } ->
                Triple("Putih Sejuk", null, 92)
            listOf("putih netral", "neutral white", "warna putih", "jadi putih").any { text.contains(it) } ->
                Triple("Putih Netral", null, 50)
            listOf("merah", "red").any { text.contains(it) } -> Triple("Merah", 0f, null)
            listOf("oranye", "orange", "jingga").any { text.contains(it) } -> Triple("Oranye", 30f, null)
            listOf("kuning", "yellow").any { text.contains(it) } -> Triple("Kuning", 55f, null)
            listOf("hijau", "green").any { text.contains(it) } -> Triple("Hijau", 125f, null)
            listOf("cyan", "biru muda", "toska").any { text.contains(it) } -> Triple("Cyan", 185f, null)
            listOf("biru", "blue").any { text.contains(it) } -> Triple("Biru", 232f, null)
            listOf("ungu", "violet", "purple").any { text.contains(it) } -> Triple("Ungu", 275f, null)
            listOf("pink", "merah muda", "magenta", "rose").any { text.contains(it) } -> Triple("Pink", 325f, null)
            else -> null
        }
    }

    private fun findLightingDevice(nonCamDevices: List<DeviceRef>): DeviceRef? {
        return nonCamDevices.firstOrNull { dev ->
            val n = dev.name.lowercase()
            val c = dev.category.lowercase()
            n.contains("bulb") || n.contains("lamp") || n.contains("bohlam") || n.contains("rgb") || c.contains("lighting") || c.contains("lampu")
        }
    }

    private fun extractRoomTarget(text: String, devices: List<DeviceRef>): String? {
        val knownRooms = listOf(
            "Ruang Tamu" to listOf("ruang tamu", "living room"),
            "Kamar Tidur" to listOf("kamar tidur", "kamar", "bedroom"),
            "Dapur" to listOf("dapur", "kitchen"),
            "Ruang Kerja" to listOf("ruang kerja", "workspace", "office")
        )
        for ((canonical, aliases) in knownRooms) {
            if (aliases.any { text.contains(it) }) {
                if (devices.any { it.room.equals(canonical, true) || aliases.any { a -> it.room.lowercase().contains(a) } }) {
                    return canonical
                }
            }
        }
        // Also match any custom user-created room present on devices
        for (dev in devices) {
            val r = dev.room.trim()
            if (r.length >= 3 && text.contains(r.lowercase())) {
                return r
            }
        }
        return null
    }

    private fun hasOnIntent(text: String): Boolean = onRegex.containsMatchIn(text)

    private fun hasOffIntent(text: String): Boolean = offRegex.containsMatchIn(text)

    private fun extractChannelIndex(text: String): Int? {
        val hasChannelNoun = listOf("switch", "saklar", "saluran", "channel", "gang", "lampu", "tombol", "l1", "l2", "l3")
            .any { text.contains(it) }
        if (!hasChannelNoun) return null

        return when {
            Regex("\\b(1|satu|pertama|l1|ch1)\\b").containsMatchIn(text) -> 0
            Regex("\\b(2|dua|kedua|l2|ch2)\\b").containsMatchIn(text) -> 1
            Regex("\\b(3|tiga|ketiga|l3|ch3)\\b").containsMatchIn(text) -> 2
            else -> null
        }
    }

    private fun findBestDeviceMatch(text: String, devices: List<DeviceRef>): DeviceRef? {
        if (devices.isEmpty()) return null

        var bestDev: DeviceRef? = null
        var bestScore = 0

        for (dev in devices) {
            var score = 0
            val lowerName = dev.name.lowercase()
            val lowerCat = dev.category.lowercase()
            val lowerRoom = dev.room.lowercase()

            if (text.contains(lowerName)) score += 10

            lowerName.split(" ", "-", "_").filter { it.length >= 3 }.forEach { token ->
                if (text.contains(token)) score += 4
            }

            if ((text.contains("saklar") || text.contains("switch")) &&
                (lowerName.contains("switch") || lowerName.contains("saklar") || lowerCat.contains("electrical"))
            ) score += 5

            if ((text.contains("lampu") || text.contains("bohlam") || text.contains("bulb") || text.contains("light")) &&
                (lowerName.contains("bulb") || lowerName.contains("lamp") || lowerName.contains("bohlam") || lowerCat.contains("lampu") || lowerCat.contains("lighting"))
            ) score += 6

            if ((text.contains("stopkontak") || text.contains("colokan") || text.contains("socket") || text.contains("plug")) &&
                (lowerName.contains("socket") || lowerName.contains("plug") || lowerName.contains("outlet") || lowerCat.contains("stop kontak"))
            ) score += 6

            if (lowerRoom.length >= 3 && text.contains(lowerRoom)) score += 3

            if (score > bestScore) {
                bestScore = score
                bestDev = dev
            }
        }

        return if (bestScore >= 4) bestDev else null
    }
}
