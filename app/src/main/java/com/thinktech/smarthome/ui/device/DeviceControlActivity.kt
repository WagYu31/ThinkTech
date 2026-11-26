package com.thinktech.smarthome.ui.device

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thinktech.smarthome.R
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.data.SmartScheduleStore
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.databinding.ActivityDeviceControlBinding
import com.thinktech.smarthome.ui.home.MainActivity
import com.thinktech.smarthome.ui.schedule.ScheduleTimeActivity
import java.util.Locale

class DeviceControlActivity : AppCompatActivity() {

    private enum class DeviceUiMode {
        SWITCH, LIGHTING, PLUG, AC, FAN, CURTAIN
    }

    private enum class LightStudioTab {
        RGB, CCT, SCENE
    }

    private lateinit var binding: ActivityDeviceControlBinding
    private val deviceRepo = TuyaDeviceRepository()

    private var devId: String = ""
    private var devName: String = "WiFi 3ch switch"
    private var deviceCategory: String = ""
    private var productId: String = ""
    private var isOnline: Boolean = true
    private var isFromPairing: Boolean = false

    private var uiMode: DeviceUiMode = DeviceUiMode.SWITCH
    private var activeChannelCount = 3
    private val channelCount = 3
    private var channelStates = BooleanArray(3) { false }
    private var channelNames = arrayOf("Switch 1", "Switch 2", "Switch 3")

    // Lighting Studio State
    private var currentLightTab: LightStudioTab = LightStudioTab.RGB
    private var lightBrightnessPct: Int = 85
    private var lightCctProgress: Int = 40 // 0 = 2700K Warm, 100 = 6500K Cool
    private var lightHue: Float = 232f
    private var lightSat: Float = 0.80f
    private var activeSceneName: String? = null
    private var isUpdatingLampSwitch: Boolean = false

    // Smart Plug Energy Telemetry State
    private var plugWatt: Double = 42.8
    private var plugVolt: Double = 221.4
    private var plugAmp: Double = 0.19
    private var plugKwhToday: Double = 0.64
    private var plugRelayMode: String = "memory" // memory / off / on

    // Climate (AC / Fan) & Curtain State
    private var climateValue: Int = 24 // 16..30°C for AC, 1..4 for Fan
    private var climateModeIndex: Int = 0
    private var curtainPercent: Int = 75

    private val devListener = object : IDevListener {
        override fun onDpUpdate(devId: String?, dpStr: String?) {
            runOnUiThread {
                try {
                    val dpsJson = JSON.parseObject(dpStr) ?: return@runOnUiThread
                    val dId = devId ?: this@DeviceControlActivity.devId
                    for (i in 0 until channelCount) {
                        val dpKey = TuyaDeviceRepository.resolveDpForChannel(dId, i)
                        val st = dpsJson.getBoolean(dpKey)
                            ?: dpsJson.getBoolean((i + 1).toString())
                            ?: dpsJson.getBoolean((101 + i).toString())
                        if (st != null) {
                            channelStates[i] = st
                            TuyaDeviceRepository.setChannel(dId, i, st)
                        }
                    }
                    if (uiMode == DeviceUiMode.PLUG) {
                        parsePlugTelemetryFromMap(dpsJson)
                    }
                    refreshActiveStudioUi()
                } catch (_: Exception) {}
            }
        }

        override fun onRemoved(devId: String?) {
            runOnUiThread {
                Toast.makeText(this@DeviceControlActivity, "Perangkat telah dihapus.", Toast.LENGTH_SHORT).show()
                navigateBack()
            }
        }

        override fun onStatusChanged(devId: String?, online: Boolean) {
            runOnUiThread {
                isOnline = online
                updateHeaderStatus()
            }
        }

        override fun onNetworkStatusChanged(devId: String?, status: Boolean) {}
        override fun onDevInfoUpdate(devId: String?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devId = intent.getStringExtra("DEV_ID") ?: "demo_dev_${System.currentTimeMillis() % 100000}"
        val rawDevName = intent.getStringExtra("DEV_NAME")
        devName = if (rawDevName.isNullOrBlank() || rawDevName.equals("null", true)) {
            "WiFi 3ch switch"
        } else {
            rawDevName
        }
        deviceCategory = intent.getStringExtra("DEVICE_CATEGORY").orEmpty()
        productId = intent.getStringExtra("PRODUCT_ID") ?: ""
        isOnline = intent.getBooleanExtra("IS_ONLINE", true)
        isFromPairing = intent.getBooleanExtra("IS_FROM_PAIRING", false)

        // Safety redirect: Cameras ALWAYS open CameraControlActivity
        if (TuyaDeviceClassifier.isCameraDevice(devId, devName, deviceCategory, productId)) {
            startActivity(Intent(this, CameraControlActivity::class.java).apply {
                putExtras(intent)
            })
            finish()
            return
        }

        uiMode = resolveDeviceUiMode()
        activeChannelCount = if (uiMode == DeviceUiMode.SWITCH) {
            TuyaDeviceClassifier.resolveChannelCount(devId, devName, deviceCategory)
        } else {
            1
        }

        val initialMasterOn = intent.getBooleanExtra("IS_ON", false)
        channelStates = TuyaDeviceRepository.getChannels(devId, channelCount).copyOf()
        if (!TuyaDeviceRepository.channelStates.containsKey(devId)) {
            channelStates.fill(false)
            channelStates[0] = initialMasterOn
        }

        // Sync real Tuya Device DPs immediately if available
        if (ThinkTechApp.isSdkInitialized && !devId.startsWith("demo_")) {
            try {
                val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
                val dps = devBean?.dps
                if (dps != null) {
                    for (i in 0 until channelCount) {
                        val dpKey = TuyaDeviceRepository.resolveDpForChannel(devId, i)
                        val st = (dps[dpKey] as? Boolean)
                            ?: (dps[(i + 1).toString()] as? Boolean)
                            ?: (dps[(101 + i).toString()] as? Boolean)
                        if (st != null) {
                            channelStates[i] = st
                            TuyaDeviceRepository.setChannel(devId, i, st)
                        }
                    }
                    if (uiMode == DeviceUiMode.PLUG) {
                        parsePlugTelemetryFromMap(dps)
                    }
                }
            } catch (_: Exception) {}
        }

        if (activeChannelCount == 1) {
            channelNames[0] = when (uiMode) {
                DeviceUiMode.LIGHTING -> "Lampu Utama"
                DeviceUiMode.PLUG -> "Stopkontak Utama"
                else -> devName
            }
        }
        channelNames = TuyaDeviceRepository.getChannelNames(devId, channelNames).copyOf()

        setupHarmonyChrome()
        setupUI()
        setupListeners()
        registerTuyaListener()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
    }

    private fun resolveDeviceUiMode(): DeviceUiMode {
        val lowerCat = deviceCategory.lowercase()
        val lowerName = devName.lowercase()
        val sdkCat = if (ThinkTechApp.isSdkInitialized && !devId.startsWith("demo_")) {
            runCatching { ThingHomeSdk.getDataInstance().getDeviceBean(devId)?.category?.lowercase().orEmpty() }.getOrDefault("")
        } else ""

        return when {
            sdkCat in setOf("dj", "dd", "xdd", "tynd") ||
                lowerCat.contains("lighting") || lowerCat.contains("lampu") ||
                lowerName.contains("bulb") || lowerName.contains("bohlam") || lowerName.contains("lamp") ||
                lowerName.contains("rgb") || lowerName.contains("dimmer") || lowerName.contains("downlight") ||
                lowerName.contains("ceiling") -> DeviceUiMode.LIGHTING

            sdkCat in setOf("cz", "pc") ||
                lowerCat.contains("stop kontak") || lowerCat.contains("socket") ||
                lowerName.contains("socket") || lowerName.contains("plug") ||
                lowerName.contains("outlet") || lowerName.contains("stopkontak") ||
                lowerName.contains("colokan") -> DeviceUiMode.PLUG

            sdkCat == "cl" || lowerName.contains("curtain") || lowerName.contains("tirai") ||
                lowerName.contains("gorden") || lowerName.contains("blind") -> DeviceUiMode.CURTAIN

            sdkCat == "kt" || lowerCat.contains("climate") ||
                lowerName.contains("smart ac") || lowerName.contains("air cond") -> DeviceUiMode.AC

            sdkCat == "fs" || lowerName.contains("fan") || lowerName.contains("kipas") -> DeviceUiMode.FAN

            else -> DeviceUiMode.SWITCH
        }
    }

    private fun setupHarmonyChrome() {
        val isLighting = uiMode == DeviceUiMode.LIGHTING
        val canvasColor = if (isLighting) Color.WHITE else Color.parseColor("#F1F3F5")
        binding.rootDeviceControl.setBackgroundColor(canvasColor)
        window.statusBarColor = canvasColor
        window.navigationBarColor = canvasColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        val density = resources.displayMetrics.density
        if (isLighting) {
            binding.btnBack.background = null
            binding.btnBack.elevation = 0f
            binding.btnEditDeviceName.background = null
            binding.btnEditDeviceName.elevation = 0f
        } else {
            binding.btnBack.elevation = 1.5f * density
            binding.btnEditDeviceName.elevation = 1.5f * density
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val microSpot = 0x120A192F
                val softAmbient = 0x0A0A192F
                binding.btnBack.outlineSpotShadowColor = microSpot
                binding.btnBack.outlineAmbientShadowColor = softAmbient
                binding.btnEditDeviceName.outlineSpotShadowColor = microSpot
                binding.btnEditDeviceName.outlineAmbientShadowColor = softAmbient
            }
        }
    }

    private fun updateHeaderStatus() {
        val isDemo = devId.startsWith("demo_")
        val primaryOn = channelStates[0]
        val activeCount = (0 until activeChannelCount).count { channelStates[it] }
        when {
            isDemo -> {
                binding.tvDeviceSubtitle.text = when (uiMode) {
                    DeviceUiMode.LIGHTING -> if (primaryOn) "Mode Demo • Menyala ${lightBrightnessPct}%" else "Mode Demo • Mati"
                    DeviceUiMode.PLUG -> if (primaryOn) "Mode Demo • ${String.format(Locale.US, "%.1f W", plugWatt)}" else "Mode Demo • Mati"
                    DeviceUiMode.AC -> "Mode Demo • ${climateValue}°C"
                    DeviceUiMode.FAN -> "Mode Demo • Level $climateValue"
                    DeviceUiMode.CURTAIN -> "Mode Demo • Terbuka ${curtainPercent}%"
                    DeviceUiMode.SWITCH -> if (activeCount > 0) "Mode Demo • $activeCount Aktif" else "Mode Demo"
                }
                binding.tvDeviceSubtitle.setTextColor(Color.parseColor("#D97706"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            }
            !isOnline -> {
                binding.tvDeviceSubtitle.text = "Offline"
                binding.tvDeviceSubtitle.setTextColor(Color.parseColor("#9398A2"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A7ACB5"))
            }
            uiMode != DeviceUiMode.SWITCH -> {
                val activeLabel = when (uiMode) {
                    DeviceUiMode.LIGHTING -> if (primaryOn) "Online • Menyala ${lightBrightnessPct}%" else "Online • Siaga"
                    DeviceUiMode.PLUG -> if (primaryOn) "Online • ${String.format(Locale.US, "%.1f W", plugWatt)}" else "Online • Siaga"
                    DeviceUiMode.AC -> if (primaryOn) "Online • ${climateValue}°C" else "Online • Siaga"
                    DeviceUiMode.FAN -> if (primaryOn) "Online • Level $climateValue" else "Online • Siaga"
                    DeviceUiMode.CURTAIN -> "Online • Terbuka ${curtainPercent}%"
                    else -> "Online"
                }
                binding.tvDeviceSubtitle.text = activeLabel
                binding.tvDeviceSubtitle.setTextColor(Color.parseColor(if (primaryOn) "#0A59F7" else "#00996B"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(
                    Color.parseColor(if (primaryOn) "#0A59F7" else "#00B37E")
                )
            }
            else -> {
                binding.tvDeviceSubtitle.text = if (activeCount > 0) {
                    "Online • $activeCount/$activeChannelCount Aktif"
                } else {
                    "Online • Siaga"
                }
                binding.tvDeviceSubtitle.setTextColor(Color.parseColor("#00996B"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00B37E"))
            }
        }
    }

    private fun setupUI() {
        binding.tvDeviceTitle.text = devName
        if (devId.startsWith("demo_")) {
            binding.tvDeviceSubtitle.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Mode Demo Interaktif")
                    .setMessage("Perangkat ini adalah simulasi interaktif lokal. Semua fitur (Warna RGB, Suhu Warna, Suasana, Monitor Daya, Timer, Jadwal) bisa langsung dicoba.\n\nUntuk mengontrol perangkat fisik Tuya, ketuk tombol (+) di halaman utama untuk melakukan pairing.")
                    .setPositiveButton("Mengerti", null)
                    .show()
            }
        }

        // Toggle between Switch Ceramic Rockers vs Specialized Scrollable Huawei Studios
        if (uiMode == DeviceUiMode.SWITCH) {
            binding.layoutBannerRename.visibility = View.VISIBLE
            binding.layoutSwitches.visibility = View.VISIBLE
            binding.scrollDeviceStudio.visibility = View.GONE
            binding.layoutBrightnessCard.visibility = View.GONE

            val density = resources.displayMetrics.density
            when (activeChannelCount) {
                1 -> {
                    binding.colGang2.visibility = View.GONE
                    binding.colGang3.visibility = View.GONE
                    val sidePad = (56 * density).toInt()
                    binding.layoutSwitches.setPadding(sidePad, (24 * density).toInt(), sidePad, (24 * density).toInt())
                    binding.tvBadgeGang1.text = "SAKLAR"
                    binding.tvBannerHint.text = "Ketuk kartu di tengah untuk menyalakan atau mematikan saklar"
                    binding.tvMasterAllOnLabel.text = "Nyalakan"
                    binding.tvMasterAllOffLabel.text = "Matikan"
                }
                2 -> {
                    binding.colGang2.visibility = View.VISIBLE
                    binding.colGang3.visibility = View.GONE
                    val sidePad = (24 * density).toInt()
                    binding.layoutSwitches.setPadding(sidePad, (24 * density).toInt(), sidePad, (24 * density).toInt())
                }
                else -> {
                    binding.colGang2.visibility = View.VISIBLE
                    binding.colGang3.visibility = View.VISIBLE
                }
            }
        } else {
            binding.layoutBannerRename.visibility = View.GONE
            binding.layoutSwitches.visibility = View.GONE
            binding.layoutBrightnessCard.visibility = View.GONE
            binding.scrollDeviceStudio.visibility = View.VISIBLE

            binding.lightingStudio.rootLightingStudio.visibility = if (uiMode == DeviceUiMode.LIGHTING) View.VISIBLE else View.GONE
            binding.plugStudio.rootPlugStudio.visibility = if (uiMode == DeviceUiMode.PLUG) View.VISIBLE else View.GONE
            binding.climateStudio.rootClimateStudio.visibility =
                if (uiMode == DeviceUiMode.AC || uiMode == DeviceUiMode.FAN || uiMode == DeviceUiMode.CURTAIN) View.VISIBLE else View.GONE

            // Customize Bottom 4-Action Dock for Specialized Devices: [Jadwal] [Timer] [Pengaturan] [Daya]
            binding.tvMasterAllOnBadge.text = "JDL"
            binding.tvMasterAllOnLabel.text = "Jadwal"

            when (uiMode) {
                DeviceUiMode.LIGHTING -> {
                    binding.tvDeviceTitle.text = "Kontrol Perangkat"
                    (binding.tvDeviceSubtitle.parent as? View)?.visibility = View.GONE
                    binding.layoutBottomActions.visibility = View.GONE
                    binding.layoutVoiceAssistant.visibility = View.GONE
                    val lp = binding.scrollDeviceStudio.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    if (lp != null) {
                        lp.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                        lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        lp.bottomMargin = 0
                        binding.scrollDeviceStudio.layoutParams = lp
                    }
                    setupLightingStudio()
                }
                DeviceUiMode.PLUG -> setupPlugStudio()
                DeviceUiMode.AC, DeviceUiMode.FAN -> setupClimateStudio(isFan = uiMode == DeviceUiMode.FAN)
                DeviceUiMode.CURTAIN -> setupCurtainStudio()
                else -> {}
            }
        }

        updateChannelNamesUi()
        refreshActiveStudioUi()
    }

    // ==================== 1. FLAGSHIP SMART LAMP STUDIO ====================

    private fun setupLightingStudio() {
        val ls = binding.lightingStudio
        val isRgbBulb = devName.contains("rgb", ignoreCase = true) || devName.contains("color", ignoreCase = true)
        currentLightTab = if (isRgbBulb) LightStudioTab.RGB else LightStudioTab.CCT
        ls.tvLampIdentityName.text = devName

        // Header Royal-Blue Power Switch (disable Material theme tint override so royal blue track + white thumb stay crisp)
        ls.switchLampPower.thumbTintList = null
        ls.switchLampPower.trackTintList = null
        ls.switchLampPower.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingLampSwitch) return@setOnCheckedChangeListener
            toggleChannel(0, isChecked)
        }

        // Royal Cobalt 3-Segment Mode Tabs: Putih | Warna | Suasana
        ls.tabLightCct.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectLightTab(LightStudioTab.CCT)
        }
        ls.tabLightRgb.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectLightTab(LightStudioTab.RGB)
        }
        ls.tabLightScene.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectLightTab(LightStudioTab.SCENE)
        }

        // Interactive Centerpiece Dial (270° White CCT Arc / 360° Precision RGB Ring + 3D Bulb)
        ls.lampDialView.onHueChanged = { hue, isFinal ->
            lightHue = hue
            activeSceneName = null
            if (!channelStates[0]) ensurePrimaryPowerOn()
            updateLightingStudioUi()
            if (isFinal) {
                publishSmartDp(listOf("work_mode"), "21", "colour")
                publishRgbColorDp(hue, lightSat, lightBrightnessPct)
            }
        }
        ls.lampDialView.onCctChanged = { cct, isFinal ->
            lightCctProgress = cct
            activeSceneName = null
            if (!channelStates[0]) ensurePrimaryPowerOn()
            updateLightingStudioUi()
            if (isFinal) {
                publishSmartDp(listOf("work_mode"), "21", "white")
                publishColorTempDp(cct)
            }
        }
        ls.lampDialView.onBulbTapped = {
            toggleChannel(0, !channelStates[0])
        }

        // 7 Curated RGB Quick Swatches
        val swatches = listOf(
            ls.swatchRgb1 to "#FF3B30",
            ls.swatchRgb2 to "#FF9500",
            ls.swatchRgb3 to "#FFCC00",
            ls.swatchRgb4 to "#34C759",
            ls.swatchRgb5 to "#00C7E6",
            ls.swatchRgb6 to "#3B5BFF",
            ls.swatchRgb7 to "#9333EA"
        )
        swatches.forEach { (view, hex) ->
            view.setOnClickListener { v ->
                animateOrbTap(v)
                val hsv = FloatArray(3)
                Color.colorToHSV(Color.parseColor(hex), hsv)
                lightHue = hsv[0]
                lightSat = hsv[1].coerceAtLeast(0.65f)
                ls.seekLightSaturation.progress = (lightSat * 100f).toInt().coerceIn(0, 100)
                activeSceneName = null
                currentLightTab = LightStudioTab.RGB
                ensurePrimaryPowerOn()
                updateLightingStudioUi()
                publishSmartDp(listOf("work_mode"), "21", "colour")
                publishRgbColorDp(lightHue, lightSat, lightBrightnessPct)
            }
        }

        // 4 Quick Kelvin Presets (Putih tab)
        ls.presetCctNight.setOnClickListener { applyCctPreset(0, 28) }
        ls.presetCctWarm.setOnClickListener { applyCctPreset(22, 68) }
        ls.presetCctNeutral.setOnClickListener { applyCctPreset(52, 85) }
        ls.presetCctCool.setOnClickListener { applyCctPreset(95, 100) }

        // 6 Flagship Ambient Scenes
        ls.sceneReading.setOnClickListener { applyLightScene("Membaca", isRgb = false, cct = 52, hex = null, bright = 90) }
        ls.sceneNight.setOnClickListener { applyLightScene("Tidur", isRgb = false, cct = 0, hex = null, bright = 18) }
        ls.sceneCozy.setOnClickListener { applyLightScene("Santai", isRgb = false, cct = 16, hex = null, bright = 55) }
        ls.sceneCinema.setOnClickListener { applyLightScene("Sinema", isRgb = true, cct = 0, hex = "#4F46E5", bright = 35) }
        ls.sceneRomantic.setOnClickListener { applyLightScene("Romantis", isRgb = true, cct = 0, hex = "#EC4899", bright = 45) }
        ls.sceneParty.setOnClickListener { applyLightScene("Pesta RGB", isRgb = true, cct = 0, hex = "#8B5CF6", bright = 100) }

        // Precision Brightness Slider (5% - 100%)
        ls.seekLightBrightness.progress = lightBrightnessPct
        ls.seekLightBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                lightBrightnessPct = progress.coerceIn(5, 100)
                if (fromUser && !channelStates[0]) ensurePrimaryPowerOn()
                updateLightingStudioUi()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (currentLightTab == LightStudioTab.RGB) {
                    publishRgbColorDp(lightHue, lightSat, lightBrightnessPct)
                } else {
                    publishBrightnessDp(lightBrightnessPct)
                }
            }
        })

        // Precision Saturation Slider (0% - 100%, visible in Warna mode)
        ls.seekLightSaturation.progress = (lightSat * 100f).toInt().coerceIn(0, 100)
        ls.seekLightSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                lightSat = (progress.coerceIn(0, 100)) / 100f
                activeSceneName = null
                if (fromUser && !channelStates[0]) ensurePrimaryPowerOn()
                updateLightingStudioUi()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                publishSmartDp(listOf("work_mode"), "21", "colour")
                publishRgbColorDp(lightHue, lightSat, lightBrightnessPct)
            }
        })

        // Bottom CTA Actions: Jadwal Otomatis ON/OFF | Timer | Pengaturan
        ls.btnLampSchedule.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showScheduleDialog()
        }
        ls.btnLampTimer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCountdownTimerDialog()
        }
        ls.btnLampSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showSwitchSettingsDialog()
        }

        selectLightTab(currentLightTab)
    }

    private fun selectLightTab(tab: LightStudioTab) {
        currentLightTab = tab
        val ls = binding.lightingStudio

        val showDial = tab == LightStudioTab.RGB || tab == LightStudioTab.CCT
        ls.panelDialStage.visibility = if (showDial) View.VISIBLE else View.GONE
        ls.panelLightScene.visibility = if (tab == LightStudioTab.SCENE) View.VISIBLE else View.GONE
        ls.panelRgbQuickRow.visibility = if (tab == LightStudioTab.RGB) View.VISIBLE else View.GONE
        ls.panelCctQuickRow.visibility = if (tab == LightStudioTab.CCT) View.VISIBLE else View.GONE
        ls.rowLightSaturation.visibility = if (tab == LightStudioTab.RGB) View.VISIBLE else View.GONE

        val tabs = listOf(
            ls.tabLightCct to LightStudioTab.CCT,
            ls.tabLightRgb to LightStudioTab.RGB,
            ls.tabLightScene to LightStudioTab.SCENE
        )
        tabs.forEach { (tv, t) ->
            if (t == tab) {
                tv.setBackgroundResource(R.drawable.bg_lamp_tab_active)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.background = null
                tv.setTextColor(Color.parseColor("#2A303D"))
            }
        }
        updateLightingStudioUi()
    }

    private fun applyCctPreset(cct: Int, brightness: Int) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        lightCctProgress = cct
        lightBrightnessPct = brightness
        activeSceneName = null
        binding.lightingStudio.seekLightBrightness.progress = brightness
        ensurePrimaryPowerOn()
        updateLightingStudioUi()
        publishSmartDp(listOf("work_mode"), "21", "white")
        publishColorTempDp(cct)
        publishBrightnessDp(brightness)
    }

    private fun applyLightScene(name: String, isRgb: Boolean, cct: Int, hex: String?, bright: Int) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        activeSceneName = name
        lightBrightnessPct = bright
        binding.lightingStudio.seekLightBrightness.progress = bright
        if (isRgb && hex != null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(Color.parseColor(hex), hsv)
            lightHue = hsv[0]
            lightSat = hsv[1]
            binding.lightingStudio.seekLightSaturation.progress = (lightSat * 100f).toInt().coerceIn(0, 100)
            publishSmartDp(listOf("work_mode"), "21", "colour")
            publishRgbColorDp(lightHue, lightSat, bright)
        } else {
            lightCctProgress = cct
            publishSmartDp(listOf("work_mode"), "21", "white")
            publishColorTempDp(cct)
            publishBrightnessDp(bright)
        }
        ensurePrimaryPowerOn()
        updateLightingStudioUi()
    }

    private fun computeCurrentLightColor(): Int {
        val useRgb = when (currentLightTab) {
            LightStudioTab.RGB -> true
            LightStudioTab.CCT -> false
            LightStudioTab.SCENE -> activeSceneName in setOf("Sinema", "Romantis", "Pesta RGB")
        }
        return if (useRgb) {
            Color.HSVToColor(floatArrayOf(lightHue, lightSat, 1f))
        } else {
            interpolateCctColor(lightCctProgress / 100f)
        }
    }

    private fun interpolateCctColor(fraction: Float): Int {
        val warm = Color.parseColor("#FFAE42")
        val neutral = Color.parseColor("#FFE8C2")
        val cool = Color.parseColor("#93C5FD")
        return if (fraction < 0.5f) {
            blendColors(warm, neutral, fraction * 2f)
        } else {
            blendColors(neutral, cool, (fraction - 0.5f) * 2f)
        }
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun updateLightingStudioUi() {
        val ls = binding.lightingStudio
        val isOn = channelStates[0]
        val activeColor = computeCurrentLightColor()
        val kelvin = 2700 + (lightCctProgress * 38)
        val kelvinLabel = when {
            kelvin < 3300 -> "Hangat"
            kelvin < 4800 -> "Netral"
            else -> "Sejuk"
        }
        val hexStr = String.format(Locale.US, "#%06X", 0xFFFFFF and activeColor)

        ls.tvLampIdentityName.text = devName

        // Sync top-right Royal Blue Power Switch without triggering recursive toggle
        isUpdatingLampSwitch = true
        if (ls.switchLampPower.isChecked != isOn) {
            ls.switchLampPower.isChecked = isOn
        }
        isUpdatingLampSwitch = false

        // Update 3D Smart Bulb + Ring/Arc Dial
        val dialMode = if (currentLightTab == LightStudioTab.CCT) {
            HarmonyLampDialView.DialMode.WHITE_CCT
        } else {
            HarmonyLampDialView.DialMode.COLOR_RGB
        }
        ls.lampDialView.setDialState(
            dialMode = dialMode,
            powerOn = isOn,
            hue = lightHue,
            saturation = lightSat,
            brightnessPct = lightBrightnessPct,
            cctProgress = lightCctProgress
        )

        // Update Device Identity Meta & Mini Dot
        val prefix = if (devId.startsWith("demo_")) "Mode Demo" else "Online"
        ls.viewLightColorDot.backgroundTintList = ColorStateList.valueOf(
            if (isOn) activeColor else Color.parseColor("#A7ACB5")
        )
        ls.tvLightColorMeta.text = when {
            !isOn -> "$prefix • Lampu Mati"
            activeSceneName != null -> "$prefix • Suasana $activeSceneName • ${lightBrightnessPct}%"
            currentLightTab == LightStudioTab.RGB -> "$prefix • Warna $hexStr • ${lightBrightnessPct}%"
            else -> "$prefix • ${kelvin}K ($kelvinLabel) • ${lightBrightnessPct}%"
        }

        // Live Hex Readout & Sliders Percentage
        ls.tvRgbReadout.text = hexStr
        ls.tvRgbReadout.setTextColor(if (isOn) Color.parseColor("#3B5BFF") else Color.parseColor("#8A909E"))
        ls.tvLightBrightnessPct.text = "${lightBrightnessPct}%"
        ls.tvLightSaturationPct.text = "${(lightSat * 100f).toInt()}%"

        // Scene Header Summary
        ls.viewSceneActiveGlow.backgroundTintList = ColorStateList.valueOf(
            if (isOn) activeColor else Color.parseColor("#A7ACB5")
        )
        ls.tvLightStateHeadline.text = when {
            !isOn -> "Lampu Mati"
            activeSceneName != null -> "Suasana Aktif • $activeSceneName"
            else -> "Pilih Suasana Pencahayaan"
        }

        // Highlight active scene card
        val sceneMap = listOf(
            ls.sceneReading to "Membaca",
            ls.sceneNight to "Tidur",
            ls.sceneCozy to "Santai",
            ls.sceneCinema to "Sinema",
            ls.sceneRomantic to "Romantis",
            ls.sceneParty to "Pesta RGB"
        )
        sceneMap.forEach { (tile, name) ->
            tile.setBackgroundResource(
                if (activeSceneName == name) R.drawable.bg_harmony_scene_tile_active else R.drawable.bg_harmony_scene_tile
            )
        }

        updateHeaderStatus()
        com.thinktech.smarthome.data.AppLanguageManager.localizeActivity(this)
    }

    // ==================== 2. HUAWEI AI LIFE SMART PLUG STUDIO ====================

    private fun setupPlugStudio() {
        val ps = binding.plugStudio
        ps.btnPlugPowerOrb.setOnClickListener { v ->
            animateOrbTap(v)
            toggleChannel(0, !channelStates[0])
        }
        ps.switchPlugChildLock.setOnCheckedChangeListener { _, isChecked ->
            publishSmartDp(listOf("child_lock"), "40", isChecked)
        }
        ps.switchPlugOvercharge.setOnCheckedChangeListener { _, isChecked ->
            publishSmartDp(listOf("overcharge_switch"), "41", isChecked)
        }
        ps.rowPlugRelayStatus.setOnClickListener {
            plugRelayMode = when (plugRelayMode) {
                "memory" -> "off"
                "off" -> "on"
                else -> "memory"
            }
            updatePlugStudioUi()
            publishSmartDp(listOf("relay_status"), "38", plugRelayMode)
        }
    }

    private fun parsePlugTelemetryFromMap(dps: Map<String, Any?>) {
        val rawPower = (dps["19"] ?: dps["cur_power"])?.toString()?.toDoubleOrNull()
        val rawVolt = (dps["20"] ?: dps["cur_voltage"])?.toString()?.toDoubleOrNull()
        val rawAmp = (dps["18"] ?: dps["cur_current"])?.toString()?.toDoubleOrNull()
        val rawEle = (dps["17"] ?: dps["add_ele"])?.toString()?.toDoubleOrNull()
        if (rawPower != null) plugWatt = rawPower / 10.0
        if (rawVolt != null) plugVolt = rawVolt / 10.0
        if (rawAmp != null) plugAmp = rawAmp / 1000.0
        if (rawEle != null) plugKwhToday = rawEle / 100.0
    }

    private fun updatePlugStudioUi() {
        val ps = binding.plugStudio
        val isOn = channelStates[0]
        val liveWatt = if (isOn) plugWatt else 0.0
        val liveAmp = if (isOn) plugAmp else 0.0
        val estRp = (plugKwhToday * 1444.7).toInt()

        ps.btnPlugPowerOrb.setBackgroundResource(
            if (isOn) R.drawable.bg_harmony_plug_orb_on else R.drawable.bg_harmony_plug_orb_off
        )
        ps.ivPlugPowerIcon.imageTintList = ColorStateList.valueOf(
            if (isOn) Color.WHITE else Color.parseColor("#9398A2")
        )
        ps.tvPlugOrbState.text = if (isOn) "ON" else "OFF"
        ps.tvPlugOrbState.setTextColor(if (isOn) Color.WHITE else Color.parseColor("#9398A2"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ps.btnPlugPowerOrb.outlineSpotShadowColor = if (isOn) 0x550A59F7 else 0x160A192F
        }

        ps.tvPlugHeadline.text = if (isOn) "Daya Stopkontak Aktif" else "Daya Stopkontak Terputus"
        ps.tvPlugLiveWattPill.text = if (isOn) {
            String.format(Locale.US, "Beban Aktif • %.1f W", liveWatt)
        } else {
            "Siaga • 0.0 W"
        }
        ps.tvPlugLiveWattPill.setTextColor(Color.parseColor(if (isOn) "#0A59F7" else "#666B75"))

        ps.tvPlugWatt.text = String.format(Locale.US, "%.1f W", liveWatt)
        ps.tvPlugVolt.text = String.format(Locale.US, "%.1f V", plugVolt)
        ps.tvPlugAmp.text = String.format(Locale.US, "%.2f A", liveAmp)
        ps.tvPlugKwhToday.text = String.format(Locale.US, "Konsumsi Hari Ini: %.2f kWh", plugKwhToday)
        ps.tvPlugEstCost.text = "≈ Rp $estRp"

        ps.tvPlugRelayValue.text = when (plugRelayMode) {
            "off" -> "Tetap Mati"
            "on" -> "Langsung Nyala"
            else -> "Ingat Terakhir"
        }

        updateSpecializedPowerDockButton(isOn)
        updateHeaderStatus()
        com.thinktech.smarthome.data.AppLanguageManager.localizeActivity(this)
    }

    // ==================== 3. CLIMATE (AC / FAN) & CURTAIN STUDIO ====================

    private fun setupClimateStudio(isFan: Boolean) {
        val cs = binding.climateStudio
        cs.panelAcStudio.visibility = View.VISIBLE
        cs.panelCurtainStudio.visibility = View.GONE

        if (isFan) {
            climateValue = 2
            cs.tvClimateUnitSub.text = "Tingkat Kecepatan Kipas"
            cs.chipClimateMode1.text = "Normal"
            cs.chipClimateMode2.text = "Natural"
            cs.chipClimateMode3.text = "Tidur"
            cs.chipClimateMode4.text = "Turbo"
        } else {
            climateValue = 24
            cs.tvClimateUnitSub.text = "Target Suhu Ruangan"
        }

        cs.btnClimateDown.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            climateValue = if (isFan) (climateValue - 1).coerceAtLeast(1) else (climateValue - 1).coerceAtLeast(16)
            ensurePrimaryPowerOn()
            updateClimateStudioUi(isFan)
            publishSmartDp(if (isFan) listOf("fan_speed") else listOf("temp_set"), if (isFan) "3" else "2", climateValue)
        }
        cs.btnClimateUp.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            climateValue = if (isFan) (climateValue + 1).coerceAtMost(4) else (climateValue + 1).coerceAtMost(30)
            ensurePrimaryPowerOn()
            updateClimateStudioUi(isFan)
            publishSmartDp(if (isFan) listOf("fan_speed") else listOf("temp_set"), if (isFan) "3" else "2", climateValue)
        }

        val modeChips = listOf(cs.chipClimateMode1, cs.chipClimateMode2, cs.chipClimateMode3, cs.chipClimateMode4)
        val dpModes = if (isFan) listOf("normal", "nature", "sleep", "strong") else listOf("cold", "wet", "wind", "auto")
        modeChips.forEachIndexed { idx, chip ->
            chip.setOnClickListener {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                climateModeIndex = idx
                ensurePrimaryPowerOn()
                updateClimateStudioUi(isFan)
                publishSmartDp(listOf("mode"), "4", dpModes[idx])
            }
        }

        cs.switchClimateSwing.setOnCheckedChangeListener { _, isChecked ->
            publishSmartDp(listOf("switch_vertical", "switch_horizontal"), "5", isChecked)
        }
    }

    private fun updateClimateStudioUi(isFan: Boolean) {
        val cs = binding.climateStudio
        val isOn = channelStates[0]
        val modeChips = listOf(cs.chipClimateMode1, cs.chipClimateMode2, cs.chipClimateMode3, cs.chipClimateMode4)
        modeChips.forEachIndexed { idx, chip ->
            val selected = idx == climateModeIndex
            chip.setBackgroundResource(if (selected) R.drawable.bg_harmony_scene_tile_active else R.drawable.bg_harmony_scene_tile)
            chip.setTextColor(Color.parseColor(if (selected) "#0A59F7" else "#666B75"))
        }
        cs.tvClimateValue.text = if (isFan) "Lv $climateValue" else "${climateValue}°C"
        cs.tvClimateModeLabel.text = if (isOn) {
            "Mode ${modeChips[climateModeIndex].text} • Aktif"
        } else {
            "Perangkat Siaga (OFF)"
        }
        updateSpecializedPowerDockButton(isOn)
        updateHeaderStatus()
        com.thinktech.smarthome.data.AppLanguageManager.localizeActivity(this)
    }

    private fun setupCurtainStudio() {
        val cs = binding.climateStudio
        cs.panelAcStudio.visibility = View.GONE
        cs.panelCurtainStudio.visibility = View.VISIBLE

        cs.seekCurtainPos.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                curtainPercent = progress
                updateCurtainStudioUi()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                publishSmartDp(listOf("percent_control"), "2", curtainPercent)
            }
        })
        cs.btnCurtainOpen.setOnClickListener {
            curtainPercent = 100
            cs.seekCurtainPos.progress = 100
            updateCurtainStudioUi()
            publishSmartDp(listOf("control"), "1", "open")
        }
        cs.btnCurtainPause.setOnClickListener {
            publishSmartDp(listOf("control"), "1", "stop")
            Toast.makeText(this, "Tirai dijeda pada posisi ${curtainPercent}%", Toast.LENGTH_SHORT).show()
        }
        cs.btnCurtainClose.setOnClickListener {
            curtainPercent = 0
            cs.seekCurtainPos.progress = 0
            updateCurtainStudioUi()
            publishSmartDp(listOf("control"), "1", "close")
        }
    }

    private fun updateCurtainStudioUi() {
        val cs = binding.climateStudio
        cs.tvCurtainPercent.text = "${curtainPercent}%"
        cs.tvCurtainStatusLabel.text = when {
            curtainPercent == 0 -> "Tirai Tertutup Penuh"
            curtainPercent == 100 -> "Tirai Terbuka Penuh"
            else -> "Tirai Terbuka ${curtainPercent}%"
        }
        updateSpecializedPowerDockButton(curtainPercent > 0)
        updateHeaderStatus()
        com.thinktech.smarthome.data.AppLanguageManager.localizeActivity(this)
    }

    private fun updateSpecializedPowerDockButton(isOn: Boolean) {
        if (uiMode == DeviceUiMode.SWITCH) return
        binding.flMasterAllOffCircle.setBackgroundResource(
            if (isOn) R.drawable.bg_harmony_action_circle_red else R.drawable.bg_harmony_action_circle_blue
        )
        binding.tvMasterAllOffBadge.text = if (isOn) "OFF" else "ON"
        binding.tvMasterAllOffBadge.setTextColor(Color.parseColor(if (isOn) "#E84026" else "#0A59F7"))
        binding.tvMasterAllOffLabel.text = if (isOn) "Matikan" else "Nyalakan"
    }

    private fun ensurePrimaryPowerOn() {
        if (!channelStates[0]) {
            channelStates[0] = true
            TuyaDeviceRepository.setChannel(devId, 0, true)
            if (ThinkTechApp.isSdkInitialized && !devId.startsWith("demo_")) {
                deviceRepo.toggleChannelDp(devId, 0, true)
            }
        }
    }

    private fun animateOrbTap(v: View) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
        }.start()
    }

    private fun refreshActiveStudioUi() {
        when (uiMode) {
            DeviceUiMode.SWITCH -> updateAllGangsUi()
            DeviceUiMode.LIGHTING -> updateLightingStudioUi()
            DeviceUiMode.PLUG -> updatePlugStudioUi()
            DeviceUiMode.AC -> updateClimateStudioUi(isFan = false)
            DeviceUiMode.FAN -> updateClimateStudioUi(isFan = true)
            DeviceUiMode.CURTAIN -> updateCurtainStudioUi()
        }
    }

    // ==================== TUYA REAL DP PUBLISHERS ====================

    private fun publishSmartDp(dpCodeCandidates: List<String>, fallbackDpId: String, value: Any) {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) return
        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val matchedEntry = devBean?.schemaMap?.entries?.firstOrNull { entry ->
                val code = entry.value.code?.lowercase().orEmpty()
                dpCodeCandidates.any { it.equals(code, ignoreCase = true) }
            }
            val dpId = matchedEntry?.key ?: fallbackDpId
            val json = JSONObject().apply { put(dpId, value) }
            ThingHomeSdk.newDeviceInstance(devId).publishDps(json.toJSONString(), object : IResultCallback {
                override fun onSuccess() {}
                override fun onError(code: String?, error: String?) {}
            })
        } catch (_: Throwable) {}
    }

    private fun publishBrightnessDp(percent: Int) {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) return
        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val schemaEntry = devBean?.schemaMap?.entries?.firstOrNull {
                val c = it.value.code?.lowercase().orEmpty()
                c == "bright_value" || c == "bright_value_v2" || c == "bright_value_1"
            }
            val dpId = schemaEntry?.key ?: "22"
            val scaledVal = if (schemaEntry?.value?.code.equals("bright_value_v2", true) || dpId == "22") {
                (percent * 10).coerceIn(10, 1000)
            } else {
                (percent * 255 / 100).coerceIn(25, 255)
            }
            val json = JSONObject().apply { put(dpId, scaledVal) }
            ThingHomeSdk.newDeviceInstance(devId).publishDps(json.toJSONString(), object : IResultCallback {
                override fun onSuccess() {}
                override fun onError(code: String?, error: String?) {}
            })
        } catch (_: Throwable) {}
    }

    private fun publishColorTempDp(progressPct: Int) {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) return
        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val schemaEntry = devBean?.schemaMap?.entries?.firstOrNull {
                val c = it.value.code?.lowercase().orEmpty()
                c == "temp_value_v2" || c == "temp_value"
            }
            val dpId = schemaEntry?.key ?: "23"
            val scaledVal = if (schemaEntry?.value?.code.equals("temp_value_v2", true) || dpId == "23") {
                (progressPct * 10).coerceIn(0, 1000)
            } else {
                (progressPct * 255 / 100).coerceIn(0, 255)
            }
            val json = JSONObject().apply { put(dpId, scaledVal) }
            ThingHomeSdk.newDeviceInstance(devId).publishDps(json.toJSONString(), object : IResultCallback {
                override fun onSuccess() {}
                override fun onError(code: String?, error: String?) {}
            })
        } catch (_: Throwable) {}
    }

    private fun publishRgbColorDp(hue: Float, sat: Float, brightPct: Int) {
        val h = hue.toInt().coerceIn(0, 360)
        val s = (sat * 1000f).toInt().coerceIn(0, 1000)
        val v = (brightPct * 10).coerceIn(10, 1000)
        val hsvHexV2 = String.format(Locale.US, "%04x%04x%04x", h, s, v)
        publishSmartDp(listOf("colour_data_v2", "colour_data"), "24", hsvHexV2)
    }

    // ==================== 4. CERAMIC 1/2/3-GANG SWITCH ROCKERS ====================

    private fun updateChannelNamesUi() {
        binding.tvGang1Name.text = channelNames[0]
        binding.tvGang2Name.text = channelNames[1]
        binding.tvGang3Name.text = channelNames[2]
    }

    private fun updateAllGangsUi() {
        updateGangUi(0, channelStates[0])
        updateGangUi(1, channelStates[1])
        updateGangUi(2, channelStates[2])
        updateHeaderStatus()
        com.thinktech.smarthome.data.AppLanguageManager.localizeActivity(this)
    }

    private fun updateGangUi(index: Int, isOn: Boolean) {
        val density = resources.displayMetrics.density
        val onCardBg = R.drawable.bg_harmony_switch_on
        val offCardBg = R.drawable.bg_harmony_switch_off
        val onLedBg = R.drawable.bg_harmony_switch_led_on
        val offLedBg = R.drawable.bg_harmony_switch_led_off
        val powerBtnOn = R.drawable.bg_harmony_switch_power_on
        val powerBtnOff = R.drawable.bg_harmony_power_off
        val chipOn = R.drawable.bg_harmony_switch_chip_on
        val chipOff = R.drawable.bg_harmony_switch_chip_off

        val accentColor = Color.parseColor("#0A59F7")
        val mutedIconColor = Color.parseColor("#666B75")
        val offStateColor = Color.parseColor("#9398A2")
        val offSubColor = Color.parseColor("#A7ACB5")
        val onSubColor = Color.parseColor("#D9FFFFFF")

        val card = when (index) {
            0 -> binding.cardGang1
            1 -> binding.cardGang2
            else -> binding.cardGang3
        }
        val badge = when (index) {
            0 -> binding.tvBadgeGang1
            1 -> binding.tvBadgeGang2
            else -> binding.tvBadgeGang3
        }
        val powerBtn = when (index) {
            0 -> binding.flGang1PowerBtn
            1 -> binding.flGang2PowerBtn
            else -> binding.flGang3PowerBtn
        }
        val icon = when (index) {
            0 -> binding.ivGang1Icon
            1 -> binding.ivGang2Icon
            else -> binding.ivGang3Icon
        }
        val stateTv = when (index) {
            0 -> binding.tvGang1State
            1 -> binding.tvGang2State
            else -> binding.tvGang3State
        }
        val stateSubTv = when (index) {
            0 -> binding.tvGang1StateSub
            1 -> binding.tvGang2StateSub
            else -> binding.tvGang3StateSub
        }
        val led = when (index) {
            0 -> binding.ledGang1
            1 -> binding.ledGang2
            else -> binding.ledGang3
        }
        val subTv = when (index) {
            0 -> binding.tvGang1Sub
            1 -> binding.tvGang2Sub
            else -> binding.tvGang3Sub
        }

        card.setBackgroundResource(if (isOn) onCardBg else offCardBg)
        badge.setBackgroundResource(if (isOn) chipOn else chipOff)
        badge.setTextColor(if (isOn) Color.WHITE else mutedIconColor)
        powerBtn.setBackgroundResource(if (isOn) powerBtnOn else powerBtnOff)
        icon.imageTintList = ColorStateList.valueOf(if (isOn) accentColor else mutedIconColor)

        stateTv.text = if (isOn) "ON" else "OFF"
        stateTv.setTextColor(if (isOn) Color.WHITE else offStateColor)
        stateTv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

        stateSubTv.text = if (isOn) "Aktif" else "Siaga"
        stateSubTv.setTextColor(if (isOn) onSubColor else offSubColor)

        led.setBackgroundResource(if (isOn) onLedBg else offLedBg)

        val chPrefix = when {
            index == 0 && activeChannelCount == 1 -> "Daya Utama"
            index == 0 -> "Saluran L1"
            index == 1 -> "Saluran L2"
            else -> "Saluran L3"
        }
        subTv.text = if (isOn) "$chPrefix • ON" else "$chPrefix • OFF"
        subTv.setTextColor(if (isOn) accentColor else offStateColor)

        card.elevation = (if (isOn) 8f else 2f) * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val spotColor = if (isOn) 0x4D0A59F7 else 0x140A192F
            card.outlineSpotShadowColor = spotColor
            card.outlineAmbientShadowColor = if (isOn) spotColor else 0x0A0A192F
        }
    }

    private fun toggleChannel(index: Int, targetState: Boolean) {
        val previousState = channelStates[index]
        channelStates[index] = targetState
        TuyaDeviceRepository.setChannel(devId, index, targetState)

        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        if (uiMode == DeviceUiMode.SWITCH) {
            val targetCard = when (index) {
                0 -> binding.cardGang1
                1 -> binding.cardGang2
                else -> binding.cardGang3
            }
            val density = resources.displayMetrics.density
            targetCard.cameraDistance = 8000f * density
            val targetTilt = if (targetState) 5.5f else -5.5f
            targetCard.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .translationZ(2f * density)
                .rotationX(targetTilt)
                .setDuration(85L)
                .withEndAction {
                    targetCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationZ(if (targetState) 8f * density else 2f * density)
                        .rotationX(0f)
                        .setDuration(160L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                        .start()
                }
                .start()
        }

        refreshActiveStudioUi()

        if (ThinkTechApp.isSdkInitialized && !devId.startsWith("demo_")) {
            val channelLabel = channelNames[index]
            deviceRepo.toggleChannelDp(
                devId = devId,
                channelIndex = index,
                targetState = targetState,
                onSuccess = {},
                onError = { code, error ->
                    runOnUiThread {
                        channelStates[index] = previousState
                        TuyaDeviceRepository.setChannel(devId, index, previousState)
                        refreshActiveStudioUi()
                        Toast.makeText(this@DeviceControlActivity, "Gagal mengontrol $channelLabel ($code): $error", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun setAllChannelsState(targetState: Boolean) {
        val previousStates = channelStates.copyOf()
        for (i in 0 until activeChannelCount) {
            channelStates[i] = targetState
        }
        TuyaDeviceRepository.setAllChannels(devId, targetState, activeChannelCount)
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        refreshActiveStudioUi()

        if (ThinkTechApp.isSdkInitialized && !devId.startsWith("demo_")) {
            deviceRepo.toggleDevicePower(
                devId = devId,
                targetState = targetState,
                onSuccess = {},
                onError = { code, error ->
                    runOnUiThread {
                        for (i in 0 until channelCount) {
                            channelStates[i] = previousStates[i]
                            TuyaDeviceRepository.setChannel(devId, i, previousStates[i])
                        }
                        refreshActiveStudioUi()
                        Toast.makeText(this@DeviceControlActivity, "Gagal mengontrol saklar ($code): $error", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { navigateBack() }
        binding.btnEditDeviceName.setOnClickListener { showEditDeviceNameDialog() }
        binding.btnDismissBanner.setOnClickListener { binding.layoutBannerRename.visibility = View.GONE }

        binding.cardGang1.setOnClickListener { toggleChannel(0, !channelStates[0]) }
        binding.cardGang2.setOnClickListener { toggleChannel(1, !channelStates[1]) }
        binding.cardGang3.setOnClickListener { toggleChannel(2, !channelStates[2]) }

        binding.cardGang1.setOnLongClickListener { showEditChannelDialog(0); true }
        binding.cardGang2.setOnLongClickListener { showEditChannelDialog(1); true }
        binding.cardGang3.setOnLongClickListener { showEditChannelDialog(2); true }

        binding.btnMasterAllOn.setOnClickListener {
            if (uiMode == DeviceUiMode.SWITCH) {
                setAllChannelsState(true)
            } else {
                showScheduleDialog()
            }
        }
        binding.btnActionTimer.setOnClickListener { showCountdownTimerDialog() }
        binding.btnActionSetting.setOnClickListener { showSwitchSettingsDialog() }
        binding.btnMasterAllOff.setOnClickListener {
            if (uiMode == DeviceUiMode.SWITCH) {
                setAllChannelsState(false)
            } else if (uiMode == DeviceUiMode.CURTAIN) {
                val target = if (curtainPercent > 0) 0 else 100
                curtainPercent = target
                binding.climateStudio.seekCurtainPos.progress = target
                updateCurtainStudioUi()
                publishSmartDp(listOf("control"), "1", if (target == 100) "open" else "close")
            } else {
                toggleChannel(0, !channelStates[0])
            }
        }

        binding.layoutVoiceAssistant.setOnClickListener { showAiCommandDialog() }
    }

    // ==================== HARMONYOS LIGHT BOTTOM SHEETS ====================

    private fun showScheduleDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tuya_schedule, null, false)
        bottomSheet.setContentView(view)
        bottomSheet.setOnShowListener {
            bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        val listContainer = view.findViewById<android.widget.LinearLayout>(R.id.layoutDeviceScheduleList)
        val emptyTv = view.findViewById<TextView>(R.id.tvDeviceScheduleEmpty)

        fun renderDeviceSchedules() {
            val schedules = SmartScheduleStore.loadForDevice(this, devId)
            listContainer.removeAllViews()
            emptyTv.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE

            schedules.forEach { schedule ->
                val row = layoutInflater.inflate(R.layout.item_smart_schedule, listContainer, false)
                val tvTime = row.findViewById<TextView>(R.id.tvScheduleItemTime)
                val tvBadge = row.findViewById<TextView>(R.id.tvScheduleItemActionBadge)
                val tvTarget = row.findViewById<TextView>(R.id.tvScheduleItemTarget)
                val tvRepeat = row.findViewById<TextView>(R.id.tvScheduleItemRepeat)
                val sw = row.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchScheduleItemEnabled)

                tvTime.text = schedule.formattedTime()
                tvTime.alpha = if (schedule.enabled) 1f else 0.45f
                tvBadge.text = if (schedule.turnOn) "ON" else "OFF"
                tvBadge.setTextColor(Color.parseColor(if (schedule.turnOn) "#3B5BFF" else "#E11D48"))
                tvTarget.text = schedule.actionSummary()
                tvRepeat.text = "${schedule.repeatSummary()} • Otomasi Cloud"

                sw.thumbTintList = null
                sw.trackTintList = null
                sw.isChecked = schedule.enabled
                sw.setOnCheckedChangeListener { _, isChecked ->
                    SmartScheduleStore.setEnabled(this, schedule.id, isChecked)
                    tvTime.alpha = if (isChecked) 1f else 0.45f
                }

                row.setOnClickListener {
                    bottomSheet.dismiss()
                    startActivity(
                        ScheduleTimeActivity.createIntent(
                            context = this,
                            scheduleId = schedule.id,
                            presetDevId = devId,
                            presetDevName = devName,
                            presetChannelCount = activeChannelCount
                        )
                    )
                }
                listContainer.addView(row)
            }
        }

        renderDeviceSchedules()

        view.findViewById<TextView>(R.id.btnAddSchedule)?.setOnClickListener {
            bottomSheet.dismiss()
            startActivity(
                ScheduleTimeActivity.createIntent(
                    context = this,
                    scheduleId = null,
                    presetDevId = devId,
                    presetDevName = devName,
                    presetChannelCount = activeChannelCount
                )
            )
        }
        bottomSheet.show()
    }

    private fun showCountdownTimerDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tuya_timer, null, false)
        bottomSheet.setContentView(view)
        bottomSheet.setOnShowListener {
            bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        val onSelect: (String, Int) -> Unit = { duration, seconds ->
            publishSmartDp(listOf("countdown_1", "countdown"), "7", seconds)
            Toast.makeText(this, "Timer diatur: Matikan otomatis setelah $duration", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }

        view.findViewById<TextView>(R.id.btnTimer15m)?.setOnClickListener { onSelect("15 Menit", 900) }
        view.findViewById<TextView>(R.id.btnTimer30m)?.setOnClickListener { onSelect("30 Menit", 1800) }
        view.findViewById<TextView>(R.id.btnTimer1h)?.setOnClickListener { onSelect("1 Jam", 3600) }
        view.findViewById<TextView>(R.id.btnCancelTimer)?.setOnClickListener {
            publishSmartDp(listOf("countdown_1", "countdown"), "7", 0)
            Toast.makeText(this, "Timer dibatalkan", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showSwitchSettingsDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tuya_switch_settings, null, false)
        bottomSheet.setContentView(view)
        bottomSheet.setOnShowListener {
            bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        view.findViewById<TextView>(R.id.btnSaveSettings)?.setOnClickListener {
            Toast.makeText(this, "Pengaturan perangkat berhasil disimpan", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }
        view.findViewById<TextView>(R.id.btnDeleteDeviceInSettings)?.setOnClickListener {
            bottomSheet.dismiss()
            showConfirmDeleteDialog()
        }

        bottomSheet.show()
    }

    private fun showConfirmDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Perangkat")
            .setMessage("Apakah Anda yakin ingin menghapus '$devName' dari rumah?")
            .setPositiveButton("Hapus") { _, _ -> executeDeleteDevice() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeDeleteDevice() {
        val targetDevId = if (devId.isNotEmpty()) devId else {
            TuyaDeviceRepository.customDevices.firstOrNull { it.name.equals(devName, ignoreCase = true) }?.devId ?: ""
        }
        deviceRepo.removeDevice(
            devId = targetDevId,
            onSuccess = {
                Toast.makeText(this, "Perangkat '$devName' berhasil dihapus", Toast.LENGTH_SHORT).show()
                navigateBack()
            },
            onError = { code, error ->
                Toast.makeText(this, "Gagal menghapus perangkat ($code): $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showEditDeviceNameDialog() {
        val input = EditText(this).apply {
            setText(devName)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Ubah Nama Perangkat")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    devName = newName
                    binding.tvDeviceTitle.text = newName
                    TuyaDeviceRepository.renameDevice(devId, newName)
                    Toast.makeText(this, "Nama perangkat berhasil diubah", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditChannelDialog(index: Int) {
        val input = EditText(this).apply {
            setText(channelNames[index])
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Ubah Nama Saluran L${index + 1}")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    channelNames[index] = newName
                    TuyaDeviceRepository.setChannelName(devId, index, newName)
                    updateChannelNamesUi()
                    Toast.makeText(this, "Nama saluran disimpan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAiCommandDialog() {
        when (uiMode) {
            DeviceUiMode.LIGHTING -> {
                val options = arrayOf(
                    "Aktifkan Mode Membaca (4500K • 90%)",
                    "Aktifkan Mode Tidur Redup (2200K • 18%)",
                    "Aktifkan Mode Sinema (Indigo RGB)",
                    "Aktifkan Mode Pesta RGB (100%)",
                    "Matikan Lampu"
                )
                AlertDialog.Builder(this)
                    .setTitle("Asisten Pintar Lampu")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> applyLightScene("Membaca", false, 52, null, 90)
                            1 -> applyLightScene("Tidur", false, 0, null, 18)
                            2 -> applyLightScene("Sinema", true, 0, "#4F46E5", 35)
                            3 -> applyLightScene("Pesta RGB", true, 0, "#8B5CF6", 100)
                            4 -> toggleChannel(0, false)
                        }
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            }
            DeviceUiMode.PLUG -> {
                val options = arrayOf(
                    "Nyalakan Stopkontak",
                    "Matikan Stopkontak",
                    "Pasang Timer Mati 30 Menit",
                    "Cek Konsumsi Daya Saat Ini"
                )
                AlertDialog.Builder(this)
                    .setTitle("Asisten Pintar Energi")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> toggleChannel(0, true)
                            1 -> toggleChannel(0, false)
                            2 -> {
                                publishSmartDp(listOf("countdown_1", "countdown"), "7", 1800)
                                Toast.makeText(this, "Timer mati otomatis 30 menit diaktifkan", Toast.LENGTH_SHORT).show()
                            }
                            3 -> {
                                val w = if (channelStates[0]) plugWatt else 0.0
                                Toast.makeText(this, String.format(Locale.US, "Beban saat ini: %.1f W • Hari ini %.2f kWh", w, plugKwhToday), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            }
            else -> {
                val options = arrayOf(
                    "Matikan Semua Saklar (All Off)",
                    "Nyalakan Semua Saklar (All On)",
                    "Buka Jadwal Otomatisasi",
                    "Cek Status Saklar"
                )
                AlertDialog.Builder(this)
                    .setTitle("Asisten Pintar ThinkTech")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> setAllChannelsState(false)
                            1 -> setAllChannelsState(true)
                            2 -> showScheduleDialog()
                            3 -> {
                                val onCount = (0 until activeChannelCount).count { channelStates[it] }
                                Toast.makeText(this, "Saat ini $onCount dari $activeChannelCount saluran sedang menyala", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun registerTuyaListener() {
        if (devId.isNotEmpty() && !devId.startsWith("demo_")) {
            deviceRepo.registerDeviceListener(devId, devListener)
        }
    }

    private fun navigateBack() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (devId.isNotEmpty() && !devId.startsWith("demo_")) {
            deviceRepo.unregisterDeviceListener(devId)
        }
    }
}
