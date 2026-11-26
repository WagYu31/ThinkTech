package com.thinktech.smarthome.data

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Locale

data class AppLanguage(
    val code: String,
    val speechTag: String,
    val badge: String,
    val nativeName: String,
    val regionSubtitle: String,
    val greetingPreview: String,
    val voiceExamplePreview: String,
    val voiceBadgeLabel: String
)

/**
 * Centralized Per-App Language Manager supporting 5 global smart-home languages:
 * Indonesian (id), English (en), Malay (ms), Simplified Chinese (zh), and Japanese (ja).
 * Persists user selection, synchronizes AppCompat per-app locales, powers Voice AI Speech/TTS
 * locale selection, and translates both static XML and dynamic runtime UI text consistently.
 */
object AppLanguageManager {

    private const val PREF_NAME = "thinktech_app_language_v1"
    private const val KEY_LANG_CODE = "selected_lang_code"
    private const val TAG_KEY_LAST_TRANSLATED = 0x54544C01
    private const val TAG_KEY_ORIGINAL_ID = 0x54544C02

    val supportedLanguages: List<AppLanguage> = listOf(
        AppLanguage(
            code = "id",
            speechTag = "id-ID",
            badge = "ID",
            nativeName = "Bahasa Indonesia",
            regionSubtitle = "Indonesia • Bawaan",
            greetingPreview = "Halo, Selamat Datang",
            voiceExamplePreview = "“Nyalakan semua lampu di seluruh ruangan”",
            voiceBadgeLabel = "Teks & Voice AI (id-ID)"
        ),
        AppLanguage(
            code = "en",
            speechTag = "en-US",
            badge = "EN",
            nativeName = "English",
            regionSubtitle = "United States / Global",
            greetingPreview = "Hello, Welcome Home",
            voiceExamplePreview = "“Turn on all the lights in the entire room”",
            voiceBadgeLabel = "UI & Voice AI (en-US)"
        ),
        AppLanguage(
            code = "ms",
            speechTag = "ms-MY",
            badge = "MS",
            nativeName = "Bahasa Melayu",
            regionSubtitle = "Malaysia / Brunei",
            greetingPreview = "Hai, Selamat Pulang",
            voiceExamplePreview = "“Hidupkan semua lampu di seluruh bilik”",
            voiceBadgeLabel = "Teks & Voice AI (ms-MY)"
        ),
        AppLanguage(
            code = "zh",
            speechTag = "zh-CN",
            badge = "中文",
            nativeName = "简体中文",
            regionSubtitle = "Chinese (Simplified)",
            greetingPreview = "你好，欢迎回家",
            voiceExamplePreview = "“打开房间里的所有灯光”",
            voiceBadgeLabel = "界面与语音助手 (zh-CN)"
        ),
        AppLanguage(
            code = "ja",
            speechTag = "ja-JP",
            badge = "JP",
            nativeName = "日本語",
            regionSubtitle = "Japanese",
            greetingPreview = "こんにちは、おかえりなさい",
            voiceExamplePreview = "“部屋のすべての照明をつけて”",
            voiceBadgeLabel = "UI・音声アシスタント (ja-JP)"
        )
    )

    fun getLanguageByCode(code: String?): AppLanguage {
        return supportedLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: supportedLanguages.first()
    }

    fun getSavedCode(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return getLanguageByCode(prefs.getString(KEY_LANG_CODE, "id")).code
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        return getLanguageByCode(getSavedCode(context))
    }

    fun getSpeechLocaleTag(context: Context): String {
        return getCurrentLanguage(context).speechTag
    }

    fun getTtsLocale(context: Context): Locale {
        return Locale.forLanguageTag(getCurrentLanguage(context).speechTag)
    }

    fun setLanguage(context: Context, code: String) {
        val lang = getLanguageByCode(code)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG_CODE, lang.code)
            .apply()
        runCatching {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.code))
        }
    }

    fun applySavedLocale(context: Context) {
        val code = getSavedCode(context)
        runCatching {
            val target = LocaleListCompat.forLanguageTags(code)
            if (AppCompatDelegate.getApplicationLocales() != target) {
                AppCompatDelegate.setApplicationLocales(target)
            }
        }
    }

    fun tr(context: Context, indonesianText: String): String {
        return translate(indonesianText, getSavedCode(context))
    }

    /**
     * Pure translation function (unit-testable without Android Context).
     * Handles exact UI labels and common dynamic patterns while preserving device names,
     * timestamps, temperatures, percentages, and hex codes.
     */
    fun translate(raw: String, langCode: String): String {
        if (langCode == "id" || raw.isBlank()) return raw

        val trimmed = raw.trim()
        EXACT_MAP[trimmed]?.get(langCode)?.let { return it }

        // Dynamic patterns
        // 1. "Perangkat (2)" -> "Devices (2)"
        Regex("^Perangkat \\((\\d+)\\)$").matchEntire(trimmed)?.let { m ->
            val n = m.groupValues[1]
            return when (langCode) {
                "en" -> "Devices ($n)"
                "ms" -> "Peranti ($n)"
                "zh" -> "设备 ($n)"
                "ja" -> "デバイス ($n)"
                else -> raw
            }
        }

        // 2. "2 Perangkat Aktif"
        Regex("^(\\d+) Perangkat Aktif$").matchEntire(trimmed)?.let { m ->
            val n = m.groupValues[1]
            return when (langCode) {
                "en" -> "$n Active Devices"
                "ms" -> "$n Peranti Aktif"
                "zh" -> "${n}个设备运行中"
                "ja" -> "${n}台が稼働中"
                else -> raw
            }
        }

        // 3. "6 pintasan instan • 2 jadwal aktif"
        Regex("^(\\d+) pintasan instan • (\\d+) jadwal aktif$").matchEntire(trimmed)?.let { m ->
            val sc = m.groupValues[1]
            val sch = m.groupValues[2]
            return when (langCode) {
                "en" -> "$sc instant scenes • $sch active schedules"
                "ms" -> "$sc pintasan segera • $sch jadual aktif"
                "zh" -> "${sc}个一键场景 • ${sch}个定时已启用"
                "ja" -> "${sc}件のシーン • ${sch}件のスケジュール"
                else -> raw
            }
        }

        // 4. "2 Aktif"
        Regex("^(\\d+) Aktif$").matchEntire(trimmed)?.let { m ->
            val n = m.groupValues[1]
            return when (langCode) {
                "en" -> "$n Active"
                "ms" -> "$n Aktif"
                "zh" -> "${n}个已启用"
                "ja" -> "${n}件有効"
                else -> raw
            }
        }

        // 5. "2 Online"
        Regex("^(\\d+) Online$").matchEntire(trimmed)?.let { m ->
            val n = m.groupValues[1]
            return when (langCode) {
                "en" -> "$n Online"
                "ms" -> "$n Dalam Talian"
                "zh" -> "${n}台在线"
                "ja" -> "${n}台オンライン"
                else -> raw
            }
        }

        // 6. Compound bullet strings like "Mode Demo • Warna #334EFF • 85%" or "Setiap Hari • Otomasi Cloud"
        if (trimmed.contains(" • ")) {
            val parts = trimmed.split(" • ")
            val translatedParts = parts.map { part -> translateCompoundSegment(part.trim(), langCode) }
            val joined = translatedParts.joinToString(" • ")
            if (joined != trimmed) return joined
        }

        return raw
    }

    private fun translateCompoundSegment(seg: String, langCode: String): String {
        EXACT_MAP[seg]?.get(langCode)?.let { return it }
        if (seg.startsWith("Warna #")) {
            val hex = seg.removePrefix("Warna ")
            return when (langCode) {
                "en" -> "Color $hex"
                "ms" -> "Warna $hex"
                "zh" -> "颜色 $hex"
                "ja" -> "カラー $hex"
                else -> seg
            }
        }
        if (seg.startsWith("Suasana ")) {
            val rest = seg.removePrefix("Suasana ")
            val tRest = EXACT_MAP[rest]?.get(langCode) ?: rest
            return when (langCode) {
                "en" -> "Scene $tRest"
                "ms" -> "Suasana $tRest"
                "zh" -> "场景 $tRest"
                "ja" -> "シーン $tRest"
                else -> seg
            }
        }
        return seg
    }

    fun localizeActivity(activity: Activity) {
        val code = getSavedCode(activity)
        if (code == "id") return
        val root = activity.window?.decorView ?: return
        localizeViewTree(root, code)
    }

    fun localizeViewTree(view: View, langCode: String) {
        if (langCode == "id") return

        if (view is BottomNavigationView) {
            val menu = view.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val title = item.title?.toString() ?: continue
                val translated = translate(title, langCode)
                if (translated != title) {
                    item.title = translated
                }
            }
        }

        if (view is EditText) {
            val hint = view.hint?.toString()
            if (!hint.isNullOrBlank()) {
                val tHint = translate(hint, langCode)
                if (tHint != hint) view.hint = tHint
            }
        } else if (view is TextView) {
            val current = view.text?.toString()
            if (!current.isNullOrBlank()) {
                val lastTranslated = view.getTag(TAG_KEY_LAST_TRANSLATED) as? String
                val source = if (lastTranslated != null && current == lastTranslated) {
                    (view.getTag(TAG_KEY_ORIGINAL_ID) as? String) ?: current
                } else {
                    view.setTag(TAG_KEY_ORIGINAL_ID, current)
                    current
                }
                val translated = translate(source, langCode)
                if (translated != current) {
                    view.setTag(TAG_KEY_LAST_TRANSLATED, translated)
                    view.text = translated
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                localizeViewTree(view.getChildAt(i), langCode)
            }
        }
    }

    // Curated 5-language dictionary (Indonesian source -> en, ms, zh, ja)
    private val EXACT_MAP: Map<String, Map<String, String>> = mapOf(
        // Bottom Navigation
        "Rumah" to mapOf("en" to "Home", "ms" to "Utama", "zh" to "首页", "ja" to "ホーム"),
        "Smart" to mapOf("en" to "Smart", "ms" to "Pintar", "zh" to "智能", "ja" to "スマート"),
        "Explore" to mapOf("en" to "Explore", "ms" to "Teroka", "zh" to "发现", "ja" to "探索"),
        "Saya" to mapOf("en" to "Me", "ms" to "Saya", "zh" to "我的", "ja" to "マイ"),

        // Home Dashboard
        "Rumah Saya" to mapOf("en" to "My Home", "ms" to "Rumah Saya", "zh" to "我的家", "ja" to "マイホーム"),
        "STATUS RUMAH" to mapOf("en" to "HOME STATUS", "ms" to "STATUS RUMAH", "zh" to "家庭状态", "ja" to "ホームステータス"),
        "Semua Terkendali" to mapOf("en" to "All Under Control", "ms" to "Semua Terkawal", "zh" to "一切尽在掌控", "ja" to "すべて正常"),
        "Siap Dihubungkan" to mapOf("en" to "Ready to Connect", "ms" to "Sedia Dihubungkan", "zh" to "准备连接", "ja" to "接続準備完了"),
        "Semua Perangkat Siaga" to mapOf("en" to "All Devices Standby", "ms" to "Semua Peranti Sedia", "zh" to "所有设备待机中", "ja" to "全デバイス待機中"),
        "Ekosistem Terhubung • Stabil" to mapOf("en" to "Ecosystem Connected • Stable", "ms" to "Ekosistem Terhubung • Stabil", "zh" to "全屋互联 • 运行稳定", "ja" to "エコシステム接続中 • 安定"),
        "Semua Perangkat Siaga • Hemat Daya" to mapOf("en" to "All Standby • Energy Saving", "ms" to "Semua Sedia • Jimat Tenaga", "zh" to "全屋待机 • 节能模式", "ja" to "全機器待機中 • 省エネ"),
        "Perangkat Offline • Periksa Wi-Fi" to mapOf("en" to "Devices Offline • Check Wi-Fi", "ms" to "Peranti Luar Talian • Semak Wi-Fi", "zh" to "设备离线 • 请检查网络", "ja" to "オフライン • Wi-Fiを確認"),
        "Hubungkan Perangkat Pintar Anda" to mapOf("en" to "Connect Your Smart Devices", "ms" to "Hubungkan Peranti Pintar Anda", "zh" to "添加您的智能设备", "ja" to "スマートデバイスを接続"),
        "Daya" to mapOf("en" to "Power", "ms" to "Kuasa", "zh" to "功耗", "ja" to "電力"),
        "Menyala" to mapOf("en" to "Active", "ms" to "Hidup", "zh" to "运行中", "ja" to "稼働中"),
        "Mati" to mapOf("en" to "Off", "ms" to "Mati", "zh" to "已关闭", "ja" to "オフ"),
        "Siaga" to mapOf("en" to "Standby", "ms" to "Sedia", "zh" to "待机", "ja" to "待機"),
        "Siap Live" to mapOf("en" to "Live Ready", "ms" to "Sedia Langsung", "zh" to "实时就绪", "ja" to "ライブ準備完了"),
        "Iklim Ideal" to mapOf("en" to "Ideal Climate", "ms" to "Iklim Ideal", "zh" to "舒适环境", "ja" to "快適環境"),
        "+ Sensor" to mapOf("en" to "+ Sensor", "ms" to "+ Sensor", "zh" to "+ 传感器", "ja" to "+ センサー"),
        "Pulang" to mapOf("en" to "Home", "ms" to "Pulang", "zh" to "回家", "ja" to "帰宅"),
        "Keluar" to mapOf("en" to "Away", "ms" to "Keluar", "zh" to "离家", "ja" to "外出"),
        "Tidur" to mapOf("en" to "Sleep", "ms" to "Tidur", "zh" to "睡眠", "ja" to "就寝"),
        "Semua" to mapOf("en" to "All", "ms" to "Semua", "zh" to "全部", "ja" to "すべて"),
        "Ruang Tamu" to mapOf("en" to "Living Room", "ms" to "Ruang Tamu", "zh" to "客厅", "ja" to "リビング"),
        "Kamar Tidur" to mapOf("en" to "Bedroom", "ms" to "Bilik Tidur", "zh" to "卧室", "ja" to "寝室"),
        "Dapur" to mapOf("en" to "Kitchen", "ms" to "Dapur", "zh" to "厨房", "ja" to "キッチン"),
        "Ruang Kerja" to mapOf("en" to "Workspace", "ms" to "Bilik Kerja", "zh" to "书房", "ja" to "書斎"),
        "Keluar dari Akun" to mapOf("en" to "Sign Out", "ms" to "Log Keluar", "zh" to "退出登录", "ja" to "ログアウト"),
        "Saklar Pintar" to mapOf("en" to "Smart Switch", "ms" to "Suis Pintar", "zh" to "智能开关", "ja" to "スマートスイッチ"),
        "Lampu Pintar" to mapOf("en" to "Smart Lighting", "ms" to "Lampu Pintar", "zh" to "智能照明", "ja" to "スマート照明"),
        "Stop Kontak Pintar" to mapOf("en" to "Smart Socket", "ms" to "Soket Pintar", "zh" to "智能插座", "ja" to "スマートプラグ"),
        "Kamera & Kunci" to mapOf("en" to "Camera & Lock", "ms" to "Kamera & Kunci", "zh" to "摄像机与门锁", "ja" to "カメラ・スマートロック"),
        "Layanan Cerdas" to mapOf("en" to "Smart Services", "ms" to "Perkhidmatan Pintar", "zh" to "智能服务", "ja" to "スマートサービス"),
        "Lainnya ›" to mapOf("en" to "More ›", "ms" to "Lagi ›", "zh" to "更多 ›", "ja" to "もっと見る ›"),
        "Catatan AI" to mapOf("en" to "AI Notes", "ms" to "Nota AI", "zh" to "AI 笔记", "ja" to "AI ノート"),
        "Ringkasan suara & interaksi alami" to mapOf("en" to "Voice summaries & natural control", "ms" to "Ringkasan suara & interaksi semula jadi", "zh" to "语音摘要与自然交互", "ja" to "音声要約と自然な操作"),
        "Buka" to mapOf("en" to "Open", "ms" to "Buka", "zh" to "打开", "ja" to "開く"),
        "Terjemahan\nLangsung" to mapOf("en" to "Live\nTranslate", "ms" to "Terjemahan\nLangsung", "zh" to "同声\n传译", "ja" to "同時\n通訳"),
        "Mode\nPercakapan" to mapOf("en" to "Voice\nAssistant", "ms" to "Mod\nPerbualan", "zh" to "语音\n对话", "ja" to "会話\nモード"),
        "Tanya ThinkTech AI..." to mapOf("en" to "Ask ThinkTech AI...", "ms" to "Tanya ThinkTech AI...", "zh" to "向 ThinkTech AI 提问...", "ja" to "ThinkTech AIに話しかける..."),

        // Smart Tab & Schedule Time
        "Otomasi" to mapOf("en" to "Automation", "ms" to "Automasi", "zh" to "自动化", "ja" to "オートメーション"),
        "Sekali Sentuh" to mapOf("en" to "Tap-to-Run", "ms" to "Satu Sentuh", "zh" to "一键执行", "ja" to "ワンタップ実行"),
        "Lampu Cepat ON" to mapOf("en" to "Quick Lights ON", "ms" to "Lampu Pantas ON", "zh" to "一键全开", "ja" to "クイック点灯"),
        "Nyalakan semua • 1 tugas" to mapOf("en" to "Turn on all • 1 task", "ms" to "Hidupkan semua • 1 tugas", "zh" to "打开全部 • 1项任务", "ja" to "すべてオン • 1件"),
        "Persiapan Tidur" to mapOf("en" to "Bedtime Prep", "ms" to "Persediaan Tidur", "zh" to "睡前准备", "ja" to "おやすみ準備"),
        "Redupkan ruang • 2 tugas" to mapOf("en" to "Dim rooms • 2 tasks", "ms" to "Malapkan ruang • 2 tugas", "zh" to "调暗灯光 • 2项任务", "ja" to "照明を調整 • 2件"),
        "Pulang ke Rumah" to mapOf("en" to "Arrive Home", "ms" to "Pulang ke Rumah", "zh" to "回到家中", "ja" to "帰宅モード"),
        "Aktifkan utama • 3 tugas" to mapOf("en" to "Power main lights • 3 tasks", "ms" to "Aktifkan utama • 3 tugas", "zh" to "开启主灯 • 3项任务", "ja" to "メイン機器オン • 3件"),
        "Mode Produktif" to mapOf("en" to "Boost Productivity", "ms" to "Mod Produktif", "zh" to "高效专注", "ja" to "集中モード"),
        "Cahaya fokus • 1 tugas" to mapOf("en" to "Bright focus light • 1 task", "ms" to "Cahaya fokus • 1 tugas", "zh" to "明亮专注光 • 1项任务", "ja" to "作業用ライト • 1件"),
        "Tinggalkan Rumah" to mapOf("en" to "Leave Home", "ms" to "Keluar Rumah", "zh" to "离家模式", "ja" to "外出モード"),
        "Matikan semua • 3 tugas" to mapOf("en" to "Turn off all • 3 tasks", "ms" to "Matikan semua • 3 tugas", "zh" to "关闭全部 • 3项任务", "ja" to "すべてオフ • 3件"),
        "Hemat Energi" to mapOf("en" to "Energy Saver", "ms" to "Jimat Tenaga", "zh" to "节能模式", "ja" to "省エネモード"),
        "Audit standby • 2 tugas" to mapOf("en" to "Standby audit • 2 tasks", "ms" to "Audit sedia • 2 tugas", "zh" to "待机检测 • 2项任务", "ja" to "待機電力チェック • 2件"),
        "Jadwal Otomasi Cloud" to mapOf("en" to "Cloud Schedules", "ms" to "Jadual Automasi Awan", "zh" to "云端定时自动化", "ja" to "クラウドスケジュール"),
        "+ Tambah Jadwal Waktu Baru" to mapOf("en" to "+ Add New Schedule", "ms" to "+ Tambah Jadual Baru", "zh" to "+ 添加新定时任务", "ja" to "+ 新しいスケジュールを追加"),
        "Atur Waktu Jadwal" to mapOf("en" to "Schedule Time", "ms" to "Tetapkan Masa Jadual", "zh" to "定时设置", "ja" to "スケジュール時刻"),
        "Edit Waktu Jadwal" to mapOf("en" to "Edit Schedule Time", "ms" to "Sunting Masa Jadual", "zh" to "编辑定时", "ja" to "スケジュール編集"),
        "Ulangi" to mapOf("en" to "Repeat", "ms" to "Ulang", "zh" to "重复", "ja" to "繰り返し"),
        "Setiap Hari" to mapOf("en" to "Every Day", "ms" to "Setiap Hari", "zh" to "每天", "ja" to "毎日"),
        "Hari Kerja" to mapOf("en" to "Weekdays", "ms" to "Hari Bekerja", "zh" to "工作日", "ja" to "平日"),
        "Akhir Pekan" to mapOf("en" to "Weekends", "ms" to "Hujung Minggu", "zh" to "周末", "ja" to "週末"),
        "Sekali Saja" to mapOf("en" to "Once", "ms" to "Sekali Sahaja", "zh" to "仅一次", "ja" to "1回のみ"),
        "Perangkat Target" to mapOf("en" to "Target Device", "ms" to "Peranti Sasaran", "zh" to "目标设备", "ja" to "対象デバイス"),
        "Semua Saklar" to mapOf("en" to "All Switches", "ms" to "Semua Suis", "zh" to "所有开关", "ja" to "すべてのスイッチ"),
        "Nyalakan (ON)" to mapOf("en" to "Turn ON", "ms" to "Hidupkan (ON)", "zh" to "开启 (ON)", "ja" to "オンにする (ON)"),
        "Matikan (OFF)" to mapOf("en" to "Turn OFF", "ms" to "Matikan (OFF)", "zh" to "关闭 (OFF)", "ja" to "オフにする (OFF)"),
        "Simpan Jadwal" to mapOf("en" to "Save Schedule", "ms" to "Simpan Jadual", "zh" to "保存定时", "ja" to "スケジュールを保存"),
        "Otomasi Cloud" to mapOf("en" to "Cloud Automation", "ms" to "Automasi Awan", "zh" to "云端自动化", "ja" to "クラウド自動化"),
        "Nyalakan" to mapOf("en" to "Turn On", "ms" to "Hidupkan", "zh" to "开启", "ja" to "オン"),
        "Matikan" to mapOf("en" to "Turn Off", "ms" to "Matikan", "zh" to "关闭", "ja" to "オフ"),

        // Device Control & Smart Lamp Studio
        "Kontrol Perangkat" to mapOf("en" to "Control Device", "ms" to "Kawalan Peranti", "zh" to "设备控制", "ja" to "デバイス操作"),
        "Mode Demo" to mapOf("en" to "Demo Mode", "ms" to "Mod Demo", "zh" to "演示模式", "ja" to "デモモード"),
        "Putih" to mapOf("en" to "White", "ms" to "Putih", "zh" to "白光", "ja" to "ホワイト"),
        "Warna" to mapOf("en" to "Color", "ms" to "Warna", "zh" to "彩光", "ja" to "カラー"),
        "Suasana" to mapOf("en" to "Scene", "ms" to "Suasana", "zh" to "场景", "ja" to "シーン"),
        "Lilin" to mapOf("en" to "Candle", "ms" to "Lilin", "zh" to "烛光", "ja" to "キャンドル"),
        "Hangat" to mapOf("en" to "Warm", "ms" to "Hangat", "zh" to "暖光", "ja" to "暖色"),
        "Netral" to mapOf("en" to "Neutral", "ms" to "Neutral", "zh" to "自然光", "ja" to "自然光"),
        "Siang" to mapOf("en" to "Daylight", "ms" to "Siang", "zh" to "日光", "ja" to "昼光色"),
        "Membaca" to mapOf("en" to "Reading", "ms" to "Membaca", "zh" to "阅读", "ja" to "読書"),
        "Santai" to mapOf("en" to "Relax", "ms" to "Santai", "zh" to "休闲", "ja" to "リラックス"),
        "Sinema" to mapOf("en" to "Cinema", "ms" to "Pawagam", "zh" to "影院", "ja" to "シネマ"),
        "Romantis" to mapOf("en" to "Romantic", "ms" to "Romantik", "zh" to "浪漫", "ja" to "ロマンチック"),
        "Pesta RGB" to mapOf("en" to "RGB Party", "ms" to "Parti RGB", "zh" to "炫彩派对", "ja" to "パーティー"),
        "Pilih Suasana Pencahayaan" to mapOf("en" to "Choose Lighting Scene", "ms" to "Pilih Suasana Cahaya", "zh" to "选择灯光场景", "ja" to "照明シーンを選択"),
        "Pilih profil atmosfer instan untuk ruangan Anda" to mapOf("en" to "Pick an instant lighting mood for your room", "ms" to "Pilih profil suasana segera untuk bilik anda", "zh" to "为您的房间选择一键氛围灯效", "ja" to "お部屋の雰囲気を一瞬で切り替え"),
        "Jadwal Otomatis ON/OFF" to mapOf("en" to "Schedule Automatic ON/OFF", "ms" to "Jadual Automatik ON/OFF", "zh" to "定时自动开关", "ja" to "自動ON/OFFスケジュール"),
        "Timer" to mapOf("en" to "Timer", "ms" to "Pemasa", "zh" to "倒计时", "ja" to "タイマー"),
        "Pengaturan" to mapOf("en" to "Settings", "ms" to "Tetapan", "zh" to "设置", "ja" to "設定"),
        "Jadwal" to mapOf("en" to "Schedule", "ms" to "Jadual", "zh" to "定时", "ja" to "スケジュール"),
        "Semua ON" to mapOf("en" to "All ON", "ms" to "Semua ON", "zh" to "全开", "ja" to "全オン"),
        "Semua OFF" to mapOf("en" to "All OFF", "ms" to "Semua OFF", "zh" to "全关", "ja" to "全オフ"),
        "Lampu Mati" to mapOf("en" to "Light Off", "ms" to "Lampu Mati", "zh" to "灯光已关闭", "ja" to "消灯中"),

        // Voice AI Full-Screen
        "Kami mendengarkan..." to mapOf("en" to "We are listening...", "ms" to "Kami sedang mendengar...", "zh" to "正在聆听...", "ja" to "聞き取っています..."),
        "Apa yang ingin Anda lakukan?" to mapOf("en" to "What do you want to do?", "ms" to "Apa yang anda mahu lakukan?", "zh" to "您想执行什么操作？", "ja" to "どのような操作を行いますか？"),
        "“Nyalakan semua lampu di seluruh ruangan”" to mapOf(
            "en" to "“Turn on all the lights in the entire room”",
            "ms" to "“Hidupkan semua lampu di seluruh bilik”",
            "zh" to "“打开房间里的所有灯光”",
            "ja" to "“部屋のすべての照明をつけて”"
        ),
        "Asisten Suara Siap" to mapOf("en" to "Voice Assistant Ready", "ms" to "Pembantu Suara Sedia", "zh" to "语音助手已就绪", "ja" to "音声アシスタント準備完了"),
        "Perintah berhasil dijalankan" to mapOf("en" to "Command executed", "ms" to "Arahan berjaya dilaksanakan", "zh" to "指令已执行", "ja" to "コマンドを実行しました"),
        "Ketik perintah suara di sini..." to mapOf("en" to "Type a voice command here...", "ms" to "Taip arahan suara di sini...", "zh" to "在此输入语音指令...", "ja" to "音声コマンドを入力..."),
        "Kirim" to mapOf("en" to "Send", "ms" to "Hantar", "zh" to "发送", "ja" to "送信"),

        // Profile / Tab Saya & App Language
        "Akun Saya" to mapOf("en" to "My Account", "ms" to "Akaun Saya", "zh" to "我的账户", "ja" to "マイアカウント"),
        "Cloud Global Aktif" to mapOf("en" to "Global Cloud Active", "ms" to "Awan Global Aktif", "zh" to "全球云服务已连接", "ja" to "グローバルクラウド有効"),
        "Bahasa Aplikasi" to mapOf("en" to "App Language", "ms" to "Bahasa Aplikasi", "zh" to "应用语言", "ja" to "アプリの言語"),
        "Cari bahasa..." to mapOf("en" to "Search language...", "ms" to "Cari bahasa...", "zh" to "搜索语言...", "ja" to "言語を検索..."),
        "Terapkan Bahasa" to mapOf("en" to "Apply Language", "ms" to "Gunakan Bahasa", "zh" to "应用语言", "ja" to "言語を適用"),
        "Pilih Bahasa" to mapOf("en" to "Select Language", "ms" to "Pilih Bahasa", "zh" to "选择语言", "ja" to "言語を選択"),
        "Manajemen Keluarga" to mapOf("en" to "Home Management", "ms" to "Pengurusan Keluarga", "zh" to "家庭管理", "ja" to "ホーム管理"),
        "Manajemen Keluarga & Ruangan" to mapOf("en" to "Home & Room Management", "ms" to "Pengurusan Keluarga & Bilik", "zh" to "家庭与房间管理", "ja" to "ホーム・部屋管理"),
        "Perangkat" to mapOf("en" to "Devices", "ms" to "Peranti", "zh" to "设备", "ja" to "デバイス"),
        "Ruangan" to mapOf("en" to "Rooms", "ms" to "Bilik", "zh" to "房间", "ja" to "部屋"),
        "Online" to mapOf("en" to "Online", "ms" to "Aktif", "zh" to "在线", "ja" to "オンライン"),
        "My Orders" to mapOf("en" to "My Orders", "ms" to "Pesanan Saya", "zh" to "我的订单", "ja" to "注文履歴"),
        "Third-party devices & services" to mapOf("en" to "Third-party devices & services", "ms" to "Peranti & perkhidmatan pihak ketiga", "zh" to "第三方设备与服务", "ja" to "サードパーティ連携"),
        "Pusat Pesan" to mapOf("en" to "Message Center", "ms" to "Pusat Mesej", "zh" to "消息中心", "ja" to "メッセージセンター"),
        "Pesanan Saya" to mapOf("en" to "My Orders", "ms" to "Pesanan Saya", "zh" to "我的订单", "ja" to "注文履歴"),
        "FAQ & Masukan" to mapOf("en" to "FAQ & Feedback", "ms" to "Soalan Lazim & Maklum Balas", "zh" to "常见问题与反馈", "ja" to "FAQ・フィードバック"),
        "Layanan Pihak Ketiga" to mapOf("en" to "Third-Party Services", "ms" to "Perkhidmatan Pihak Ketiga", "zh" to "第三方语音服务", "ja" to "外部連携サービス"),
        "Pengaturan Aplikasi" to mapOf("en" to "App Settings", "ms" to "Tetapan Aplikasi", "zh" to "应用设置", "ja" to "アプリ設定"),
        "Keluar Akun" to mapOf("en" to "Sign Out", "ms" to "Log Keluar", "zh" to "退出登录", "ja" to "ログアウト"),
        "Dukungan Multi-Protocol IoT" to mapOf("en" to "Multi-Protocol IoT Support", "ms" to "Sokongan IoT Pelbagai Protokol", "zh" to "多协议物联网支持", "ja" to "マルチプロトコルIoT対応"),
        "Kontrol Suara Lintas Platform" to mapOf("en" to "Cross-Platform Voice Control", "ms" to "Kawalan Suara Merentas Platform", "zh" to "跨平台语音控制", "ja" to "マルチプラットフォーム音声操作"),
        "Perubahan bahasa langsung diterapkan ke seluruh halaman dan Asisten Suara (Voice AI)." to mapOf(
            "en" to "Language changes apply immediately across all screens and Voice AI.",
            "ms" to "Perubahan bahasa diterapkan serta-merta ke semua halaman dan Pembantu Suara.",
            "zh" to "语言更改将立即应用于所有页面和智能语音助手。",
            "ja" to "言語の変更はすべての画面と音声アシスタントに即時反映されます。"
        )
    )
}
