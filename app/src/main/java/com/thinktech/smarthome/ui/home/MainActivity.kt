package com.thinktech.smarthome.ui.home

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thinktech.smarthome.R
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.data.AppLanguageManager
import com.thinktech.smarthome.data.SmartSchedule
import com.thinktech.smarthome.data.SmartScheduleStore
import com.thinktech.smarthome.data.TuyaAuthRepository
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.data.model.SmartDeviceItem
import com.thinktech.smarthome.databinding.ActivityMainBinding
import com.thinktech.smarthome.databinding.PopupHomeAddMenuBinding
import com.thinktech.smarthome.ui.device.CameraControlActivity
import com.thinktech.smarthome.ui.login.LoginActivity
import com.thinktech.smarthome.ui.schedule.ScheduleTimeActivity
import com.thinktech.smarthome.ui.settings.AppLanguageActivity
import com.thinktech.smarthome.ui.pairing.PairDeviceActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authRepo = TuyaAuthRepository()
    private val deviceRepo = TuyaDeviceRepository()
    private lateinit var deviceAdapter: DeviceAdapter
    var allDevicesList: List<SmartDeviceItem> = emptyList()
    private var currentRoomFilter: String = "ALL"
    private val activeDeviceListeners = mutableMapOf<String, IDevListener>()
    private var topMenuPopup: PopupWindow? = null
    private var heroLiveDotAnimator: ObjectAnimator? = null
    private lateinit var voiceAssistant: HarmonyVoiceAssistantController

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        voiceAssistant.onMicPermissionResult(granted)
    }

    private val fallbackSpeechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            voiceAssistant.onFallbackSpeechResult(spoken)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceAssistant = HarmonyVoiceAssistantController(
            activity = this,
            micPermissionLauncher = micPermissionLauncher,
            fallbackSpeechLauncher = fallbackSpeechLauncher,
            onExecuteCommand = { spokenText -> executeVoiceCommand(spokenText) }
        )

        setupHarmonyElevations()
        setupBottomNavigation()
        setupRecyclerView()
        setupUserInfo()
        setupRoomFilters()
        setupAiFeatures()
        setupScenarioListeners()
        setupExploreListeners()
        setupProfileListeners()
        setupGeneralListeners()

        loadDevices()
    }

    private var isSmartAutomationSubTab = false

    private val scheduleEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectSmartSubTab(automationMode = true)
        }
        refreshSmartAutomations()
    }

    override fun onResume() {
        super.onResume()
        binding.tvCurrentAppLanguage.text = AppLanguageManager.getCurrentLanguage(this).nativeName
        setupUserInfo()
        refreshRoomTabs()
        loadDevices()
        refreshSmartAutomations()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadDevices()
    }

    private fun setupHarmonyElevations() {
        val density = resources.displayMetrics.density
        binding.layoutIndoorCard.setCornerRadiusDp(26f)
        binding.layoutIndoorCard.heroMood = HarmonyHomeHeroCard.HeroMood.ACTIVE
        binding.layoutIndoorCard.elevation = 3f * density

        // Whisper-subtle status indicator fade (calm HarmonyOS hardware feel)
        heroLiveDotAnimator?.cancel()
        heroLiveDotAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.viewHeroLiveDot,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 1.0f)
        ).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val softShadow = 0x160A192F
            val softAmbient = 0x0B0A192F
            listOf(
                binding.layoutIndoorCard,
                binding.layoutFloatingAiBar,
                binding.btnPlusPill,
                binding.btnTopAddDevice
            ).forEach { v ->
                v.outlineSpotShadowColor = softShadow
                v.outlineAmbientShadowColor = softAmbient
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(binding.swipeRefresh, showAiBar = true)
                    true
                }
                R.id.nav_smart -> {
                    showTab(binding.tabSmartContent, showAiBar = false)
                    refreshSmartAutomations()
                    true
                }
                R.id.nav_explore -> {
                    showTab(binding.tabExploreContent, showAiBar = false)
                    true
                }
                R.id.nav_me -> {
                    showTab(binding.tabMeContent, showAiBar = false)
                    true
                }
                else -> false
            }
        }
    }

    private fun showTab(activeView: View, showAiBar: Boolean) {
        val tabs = listOf(binding.swipeRefresh, binding.tabSmartContent, binding.tabExploreContent, binding.tabMeContent)
        tabs.forEach { tab ->
            if (tab == activeView) {
                if (tab.visibility != View.VISIBLE) {
                    tab.alpha = 0f
                    tab.visibility = View.VISIBLE
                    tab.animate().alpha(1f).setDuration(150L).start()
                }
            } else {
                tab.visibility = View.GONE
            }
        }
        val aiVis = if (showAiBar) View.VISIBLE else View.GONE
        binding.layoutFloatingAiBar.visibility = aiVis
        binding.viewAiBottomScrim.visibility = aiVis
        AppLanguageManager.localizeActivity(this)
    }

    private fun setupUserInfo() {
        val user = authRepo.getCurrentUser()
        val prefs = getSharedPreferences(TuyaAuthRepository.PREFS_NAME, MODE_PRIVATE)
        val savedName = prefs.getString(TuyaAuthRepository.KEY_SAVED_USER_NICKNAME, null)
        val savedAcc = prefs.getString(TuyaAuthRepository.KEY_SAVED_ACCOUNT, null)
        val savedHomeName = prefs.getString(ProfileDialogsHelper.KEY_TUYA_HOME_NAME, null)
        val savedRoomsStr = prefs.getString(ProfileDialogsHelper.KEY_TUYA_ROOMS, null)

        val uEmail = user?.email
        val uUsername = user?.username
        val uNick = user?.nickName

        val cleanEmail = when {
            !savedAcc.isNullOrBlank() && savedAcc.contains("@") -> savedAcc
            !uEmail.isNullOrBlank() -> uEmail
            !uUsername.isNullOrBlank() && uUsername.contains("_at_") -> {
                uUsername.replace("_at_", "@").replace("_com", ".com")
            }
            else -> "cingire687@gmail.com"
        }

        val displayName = when {
            !savedName.isNullOrBlank() -> savedName
            !uNick.isNullOrBlank() -> uNick
            cleanEmail.contains("@") -> cleanEmail.substringBefore("@")
            !uUsername.isNullOrBlank() -> {
                if (uUsername.contains("_at_")) uUsername.substringBefore("_at_") else uUsername
            }
            else -> "cingire687"
        }

        binding.tvProfileName.text = displayName
        binding.tvProfileEmail.text = cleanEmail
        binding.tvHomeName.text = savedHomeName?.takeIf { it.isNotBlank() } ?: "Rumah Saya"

        val roomCount = if (!savedRoomsStr.isNullOrBlank()) {
            savedRoomsStr.split("|").count { it.isNotBlank() }.coerceAtLeast(1)
        } else {
            4
        }
        binding.tvProfileRoomCount.text = roomCount.toString()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(
            onDeviceClicked = { device ->
                TuyaDeviceClassifier.openDeviceControl(
                    context = this,
                    devId = device.devId,
                    name = device.name,
                    category = device.category,
                    productId = device.productId,
                    isOnline = device.isOnline,
                    isSwitchOn = device.isSwitchOn
                )
            },
            onTogglePower = { device, isChecked ->
                TuyaDeviceRepository.setAllChannels(device.devId, isChecked)
                allDevicesList = allDevicesList.map {
                    if (it.devId == device.devId) it.copy(isSwitchOn = isChecked) else it.copy()
                }
                applyRoomFilter()
                updateHomeDashboardMetrics()

                deviceRepo.toggleDevicePower(
                    devId = device.devId,
                    targetState = isChecked,
                    onSuccess = {
                        runOnUiThread {
                            Toast.makeText(this, "${device.name}: ${if (isChecked) "Menyala" else "Mati"}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { code, error ->
                        runOnUiThread {
                            TuyaDeviceRepository.setAllChannels(device.devId, !isChecked)
                            allDevicesList = allDevicesList.map {
                                if (it.devId == device.devId) it.copy(isSwitchOn = !isChecked) else it.copy()
                            }
                            applyRoomFilter()
                            updateHomeDashboardMetrics()
                            Toast.makeText(this, "Gagal mengubah status ($code): $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            onDeviceLongClicked = { device ->
                showDeleteDeviceDialog(device)
            }
        )

        val density = resources.displayMetrics.density
        val gutterPx = (12 * density).toInt()
        binding.rvDevices.layoutManager = GridLayoutManager(this, 2)
        binding.rvDevices.itemAnimator = null
        if (binding.rvDevices.itemDecorationCount == 0) {
            binding.rvDevices.addItemDecoration(GridSpacingItemDecoration(spanCount = 2, spacingPx = gutterPx))
        }
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun updateHomeDashboardMetrics() {
        val total = allDevicesList.size
        val onlineCount = allDevicesList.count { it.isOnline }
        val activeCount = allDevicesList.count { dev ->
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            dev.isOnline && (isCam || dev.isSwitchOn)
        }
        val estimatedWatts = allDevicesList.fold(0) { acc, dev ->
            if (!dev.isOnline) return@fold acc
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            val isPlug = dev.category.equals("cz", ignoreCase = true) ||
                dev.category.equals("pc", ignoreCase = true) ||
                dev.name.contains("plug", ignoreCase = true) ||
                dev.name.contains("socket", ignoreCase = true) ||
                dev.name.contains("stop kontak", ignoreCase = true)
            acc + when {
                isCam -> 5
                isPlug && dev.isSwitchOn -> 15
                dev.isSwitchOn -> 12
                else -> 0
            }
        }

        binding.tvConnectedDevicesTitle.text = "Perangkat ($total)"
        binding.tvOnlineCountBadge.text = "$onlineCount Online"
        binding.tvStatOnline.text = "$onlineCount/$total"
        binding.tvStatActive.text = activeCount.toString()
        binding.tvStatActive.setTextColor(
            ContextCompat.getColor(
                this,
                if (activeCount > 0) R.color.harmony_blue else R.color.harmony_ink
            )
        )
        binding.tvStatPower.text = "$estimatedWatts W"
        binding.viewHeroLiveDot.setBackgroundResource(
            if (onlineCount > 0) R.drawable.bg_hero_dot_emerald else R.drawable.bg_hero_dot_slate
        )

        binding.tvHomeSummarySub.text = when {
            total == 0 -> "Siap Dihubungkan"
            activeCount == 0 -> "Semua Perangkat Siaga"
            else -> "$activeCount Perangkat Aktif"
        }
        binding.tvHomeSummaryDetail.text = when {
            total == 0 -> "Ketuk + untuk menambah perangkat"
            onlineCount == 0 -> "Perangkat offline · Periksa Wi-Fi"
            else -> "Iklim Ideal · Kelembapan 52%"
        }

        binding.layoutIndoorCard.heroMood = when {
            activeCount > 0 -> HarmonyHomeHeroCard.HeroMood.ACTIVE
            onlineCount > 0 -> HarmonyHomeHeroCard.HeroMood.STANDBY
            else -> HarmonyHomeHeroCard.HeroMood.OFFLINE
        }

        binding.layoutHeroEmptyState.visibility = if (total == 0) View.VISIBLE else View.GONE
        binding.tvProfileDeviceCount.text = total.toString()
        binding.tvProfileOnlineCount.text = onlineCount.toString()
        AppLanguageManager.localizeActivity(this)
    }

    private fun showDeleteDeviceDialog(device: SmartDeviceItem) {
        val openLabel = if (TuyaDeviceClassifier.isCameraDevice(device.devId, device.name, device.category, device.productId)) {
            "Buka Live Kamera"
        } else {
            "Buka Kontrol Perangkat"
        }
        val options = listOf(openLabel, "Atur Ruangan (Pindah Ruangan)", "Hapus Perangkat Dari Rumah")
        ProfileDialogsHelper.showTuyaOptionsDialog(this, "${device.name} (Lokasi: ${device.room})", options) { which ->
            when (which) {
                0 -> {
                    TuyaDeviceClassifier.openDeviceControl(
                        context = this,
                        devId = device.devId,
                        name = device.name,
                        category = device.category,
                        productId = device.productId,
                        isOnline = device.isOnline,
                        isSwitchOn = device.isSwitchOn
                    )
                }
                1 -> {
                    val prefs = getSharedPreferences(TuyaAuthRepository.PREFS_NAME, MODE_PRIVATE)
                    val savedRoomsStr = prefs.getString(ProfileDialogsHelper.KEY_TUYA_ROOMS, null)
                    val rooms = if (!savedRoomsStr.isNullOrBlank()) {
                        savedRoomsStr.split("|").filter { it.isNotBlank() }
                    } else {
                        listOf("Ruang Tamu", "Kamar Tidur", "Dapur", "Ruang Kerja")
                    }

                    ProfileDialogsHelper.showTuyaOptionsDialog(
                        this,
                        "Pindahkan '${device.name}' ke:",
                        rooms.map { if (it == device.room) "$it  ✓ (Aktif)" else it }
                    ) { roomIndex ->
                        val targetRoom = rooms[roomIndex]
                        TuyaDeviceRepository.setDeviceRoom(device.devId, targetRoom)
                        device.room = targetRoom
                        allDevicesList = allDevicesList.map {
                            if (it.devId == device.devId) it.copy(room = targetRoom) else it
                        }
                        applyRoomFilter()
                        Toast.makeText(this, "${device.name} berhasil dipindahkan ke $targetRoom!", Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> {
                    ProfileDialogsHelper.showTuyaConfirmDialog(
                        this,
                        title = "Hapus Perangkat",
                        message = "Apakah Anda yakin ingin menghapus '${device.name}' dari rumah?",
                        confirmText = "Hapus",
                        confirmColor = Color.parseColor("#E84026")
                    ) {
                        deviceRepo.removeDevice(
                            devId = device.devId,
                            onSuccess = {
                                Toast.makeText(this, "Perangkat '${device.name}' berhasil dihapus dari rumah", Toast.LENGTH_SHORT).show()
                                loadDevices()
                            },
                            onError = { code, error ->
                                Toast.makeText(this, "Gagal menghapus perangkat ($code): $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    private data class RoomTabHolder(
        val container: View,
        val label: TextView,
        val indicator: View,
        val filterKey: String
    )

    private var activeRoomTabHolders: List<RoomTabHolder> = emptyList()

    private fun setupRoomFilters() {
        refreshRoomTabs()
    }

    fun getRegisteredRooms(): List<String> {
        val prefs = getSharedPreferences(TuyaAuthRepository.PREFS_NAME, MODE_PRIVATE)
        val savedRoomsStr = prefs.getString(ProfileDialogsHelper.KEY_TUYA_ROOMS, null)
        return if (!savedRoomsStr.isNullOrBlank()) {
            savedRoomsStr.split("|").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf("Ruang Tamu", "Kamar Tidur", "Dapur", "Ruang Kerja")
        }
    }

    fun refreshRoomTabs() {
        val rooms = getRegisteredRooms()
        binding.tvProfileRoomCount.text = rooms.size.toString()

        // If previously selected room was deleted, fall back cleanly to ALL
        if (currentRoomFilter != "ALL" && rooms.none { it.equals(currentRoomFilter, ignoreCase = true) }) {
            currentRoomFilter = "ALL"
        }

        val container = binding.layoutRoomTabsContainer
        container.removeAllViews()

        val density = resources.displayMetrics.density
        val tabDefs = buildList {
            add("ALL" to "Semua")
            rooms.forEach { roomName -> add(roomName to roomName) }
        }

        val holders = mutableListOf<RoomTabHolder>()
        tabDefs.forEachIndexed { idx, (filterKey, rawLabel) ->
            val col = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                setPadding(0, 0, 0, (2 * density).toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = ((if (idx == tabDefs.lastIndex) 8 else 22) * density).toInt()
                }
            }

            val tvLabel = TextView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                maxLines = 1
                isSingleLine = true
                text = AppLanguageManager.tr(this@MainActivity, rawLabel)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.harmony_ink_muted))
            }

            val indicator = View(this).apply {
                setBackgroundResource(R.drawable.bg_harmony_tab_indicator)
                visibility = View.INVISIBLE
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (20 * density).toInt(),
                    (3 * density).toInt()
                ).apply {
                    topMargin = (5 * density).toInt()
                }
            }

            col.addView(tvLabel)
            col.addView(indicator)
            container.addView(col)

            val holder = RoomTabHolder(col, tvLabel, indicator, filterKey)
            holders.add(holder)
        }

        activeRoomTabHolders = holders
        holders.forEach { tab ->
            tab.container.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentRoomFilter = tab.filterKey
                updateRoomTabsUI(holders, tab)
                applyRoomFilter()
            }
        }

        val selected = holders.firstOrNull { it.filterKey.equals(currentRoomFilter, ignoreCase = true) }
            ?: holders.firstOrNull()
        if (selected != null) {
            updateRoomTabsUI(holders, selected)
        }
    }

    fun refreshHomeName(homeName: String) {
        if (homeName.isNotBlank()) {
            binding.tvHomeName.text = homeName
            binding.tvSmartHomeTitle.text = homeName
        }
    }

    private fun updateRoomTabsUI(tabs: List<RoomTabHolder>, selected: RoomTabHolder) {
        val fontBold = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_bold)
        val fontMedium = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_medium)
        val inkActive = ContextCompat.getColor(this, R.color.harmony_ink)
        val inkMuted = ContextCompat.getColor(this, R.color.harmony_ink_muted)

        tabs.forEach { tab ->
            if (tab == selected) {
                tab.label.setTextColor(inkActive)
                tab.label.textSize = 14.5f
                tab.label.typeface = fontBold
                tab.indicator.visibility = View.VISIBLE
            } else {
                tab.label.setTextColor(inkMuted)
                tab.label.textSize = 14f
                tab.label.typeface = fontMedium
                tab.indicator.visibility = View.INVISIBLE
            }
        }
    }

    fun applyRoomFilter() {
        val filtered = when {
            currentRoomFilter == "ALL" -> allDevicesList
            currentRoomFilter.equals("Ruang Tamu", true) || currentRoomFilter.equals("Living Room", true) ->
                allDevicesList.filter { it.room.equals("Ruang Tamu", true) || it.room.contains("Living", true) }
            currentRoomFilter.equals("Kamar Tidur", true) || currentRoomFilter.equals("Bedroom", true) ->
                allDevicesList.filter { it.room.equals("Kamar Tidur", true) || it.room.contains("Bedroom", true) }
            currentRoomFilter.equals("Dapur", true) || currentRoomFilter.equals("Kitchen", true) ->
                allDevicesList.filter { it.room.equals("Dapur", true) || it.room.contains("Kitchen", true) }
            currentRoomFilter.equals("Ruang Kerja", true) || currentRoomFilter.equals("Workspace", true) ->
                allDevicesList.filter {
                    it.room.equals("Ruang Kerja", true) || it.room.contains("Work", true) || it.room.contains("Office", true)
                }
            else -> allDevicesList.filter { it.room.equals(currentRoomFilter, ignoreCase = true) }
        }
        deviceAdapter.submitList(filtered.map { it.copy() })

        if (filtered.isEmpty()) {
            binding.rvDevices.visibility = View.GONE
            binding.layoutEmpty.visibility = if (allDevicesList.isEmpty()) View.GONE else View.VISIBLE
        } else {
            binding.rvDevices.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun setupAiFeatures() {
        binding.btnHeroAddDevice.setOnClickListener {
            openPairingScreen()
        }

        binding.btnPlusPill.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAiCommandDialog()
        }

        val openSensorPrompt = View.OnClickListener {
            ProfileDialogsHelper.showTuyaConfirmDialog(
                this,
                title = "Sensor Iklim & Lingkungan",
                message = "Hubungkan sensor suhu, kelembaban, dan kualitas udara ThinkTech untuk pemantauan iklim ruangan real-time di kartu beranda.",
                confirmText = "Tambah Sensor",
                confirmColor = ContextCompat.getColor(this, R.color.harmony_blue)
            ) {
                openPairingScreen()
            }
        }
        binding.btnAddIndoor.setOnClickListener(openSensorPrompt)
        binding.chipClimateSensor.setOnClickListener(openSensorPrompt)

        binding.chipOnlineMetric.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentRoomFilter = "ALL"
            activeRoomTabHolders.firstOrNull { tab -> tab.filterKey == "ALL" }?.let { allTab ->
                updateRoomTabsUI(activeRoomTabHolders, allTab)
            }
            applyRoomFilter()
            binding.tabHomeContent.smoothScrollTo(0, binding.tvConnectedDevicesTitle.top)
        }
        binding.chipActiveMetric.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val activeNames = allDevicesList
                .filter { dev ->
                    val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
                    dev.isOnline && (isCam || dev.isSwitchOn)
                }
                .map { dev -> dev.name }
            val msg = if (activeNames.isEmpty()) {
                AppLanguageManager.tr(this, "Semua Perangkat Siaga")
            } else {
                activeNames.joinToString(" • ")
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        binding.chipPowerMetric.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val watts = binding.tvStatPower.text?.toString() ?: "0 W"
            val activeSwitches = allDevicesList.count { dev ->
                !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) && dev.isSwitchOn
            }
            ProfileDialogsHelper.showTuyaConfirmDialog(
                this,
                title = "Audit Hemat Energi",
                message = if (activeSwitches > 0) {
                    "Estimasi daya aktif saat ini $watts ($activeSwitches saklar/lampu menyala). Matikan semua untuk hemat listrik?"
                } else {
                    "Estimasi daya saat ini $watts. Seluruh saklar dalam mode siaga hemat energi."
                },
                confirmText = if (activeSwitches > 0) "Matikan Semua" else "Selesai",
                confirmColor = ContextCompat.getColor(this, R.color.harmony_blue)
            ) {
                if (activeSwitches > 0) {
                    toggleAllNonCameraDevices(false)
                }
            }
        }

        // Home Quick-Scene Bar (1-tap HarmonyOS actions right on Home)
        binding.btnQuickHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleAllNonCameraDevices(true)
            Toast.makeText(this, "Mode Pulang aktif — perangkat utama dinyalakan", Toast.LENGTH_SHORT).show()
        }
        binding.btnQuickLeave.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleAllNonCameraDevices(false)
            Toast.makeText(this, "Mode Keluar aktif — semua saklar dimatikan", Toast.LENGTH_SHORT).show()
        }
        binding.btnQuickSleep.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            runSleepScenario()
            Toast.makeText(this, "Mode Tidur aktif", Toast.LENGTH_SHORT).show()
        }

        binding.tvSeeMoreRecommended.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_explore
        }

        binding.btnAddNotes.setOnClickListener {
            ProfileDialogsHelper.showTuyaInfoDialog(
                this,
                title = "Catatan AI",
                message = "Catatan AI ThinkTech merekam, meringkas, dan menyinkronkan interaksi suara dengan perangkat pintar Anda secara otomatis.",
                positiveText = "Mengerti"
            )
        }

        binding.tileSimultaneous.setOnClickListener {
            Toast.makeText(this, "Terjemahan Langsung: Mode AI Aktif", Toast.LENGTH_SHORT).show()
        }

        binding.tileConversation.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            voiceAssistant.openAssistant(autoListen = true)
        }

        binding.btnAiVoiceMic.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            voiceAssistant.openAssistant(autoListen = true)
        }

        binding.layoutFloatingAiBar.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            voiceAssistant.openAssistant(autoListen = true)
        }
    }

    private fun executeVoiceCommand(spokenText: String): Pair<Boolean, String> {
        val refs = allDevicesList.map { dev ->
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            val savedChNames = TuyaDeviceRepository.channelNames[dev.devId]?.toList()
                ?: listOf("Switch 1", "Switch 2", "Switch 3")
            VoiceCommandParser.DeviceRef(
                devId = dev.devId,
                name = dev.name,
                category = dev.category,
                room = dev.room,
                isCamera = isCam,
                channelNames = savedChNames
            )
        }

        return when (val action = VoiceCommandParser.parse(spokenText, refs)) {
            is VoiceCommandParser.Action.ToggleAll -> {
                toggleAllNonCameraDevices(action.turnOn)
                true to if (action.turnOn) {
                    "Siap, semua saklar dan lampu berhasil dinyalakan."
                } else {
                    "Siap, semua saklar dan lampu berhasil dimatikan."
                }
            }
            is VoiceCommandParser.Action.ToggleRoom -> {
                val roomDevices = allDevicesList.filter { dev ->
                    !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) &&
                        dev.room.equals(action.roomName, ignoreCase = true)
                }
                if (roomDevices.isEmpty()) {
                    false to "Belum ada perangkat saklar atau lampu di ${action.roomName}."
                } else {
                    val targetIds = roomDevices.map { it.devId }.toSet()
                    allDevicesList = allDevicesList.map { d ->
                        if (d.devId in targetIds) {
                            TuyaDeviceRepository.setAllChannels(d.devId, action.turnOn)
                            d.copy(isSwitchOn = action.turnOn)
                        } else d.copy()
                    }
                    applyRoomFilter()
                    updateHomeDashboardMetrics()
                    roomDevices.forEach { dev ->
                        deviceRepo.toggleDevicePower(dev.devId, action.turnOn, {}, { _, _ -> })
                    }
                    val st = if (action.turnOn) "dinyalakan" else "dimatikan"
                    true to "Siap, ${roomDevices.size} perangkat di ${action.roomName} berhasil $st."
                }
            }
            is VoiceCommandParser.Action.ToggleSwitchChannel -> {
                TuyaDeviceRepository.setChannel(action.devId, action.channelIndex, action.turnOn)
                val anyOn = TuyaDeviceRepository.getChannels(action.devId).any { it }
                allDevicesList = allDevicesList.map { d ->
                    if (d.devId == action.devId) d.copy(isSwitchOn = anyOn) else d.copy()
                }
                applyRoomFilter()
                updateHomeDashboardMetrics()
                deviceRepo.toggleChannelDp(action.devId, action.channelIndex, action.turnOn)
                val stateLabel = if (action.turnOn) "dinyalakan" else "dimatikan"
                true to "Siap, ${action.channelLabel} pada ${action.deviceName} telah $stateLabel."
            }
            is VoiceCommandParser.Action.ToggleDevice -> {
                TuyaDeviceRepository.setAllChannels(action.devId, action.turnOn)
                allDevicesList = allDevicesList.map { d ->
                    if (d.devId == action.devId) d.copy(isSwitchOn = action.turnOn) else d.copy()
                }
                applyRoomFilter()
                updateHomeDashboardMetrics()
                deviceRepo.toggleDevicePower(action.devId, action.turnOn, {}, { _, _ -> })
                val stateLabel = if (action.turnOn) "dinyalakan" else "dimatikan"
                true to "Siap, ${action.deviceName} telah $stateLabel."
            }

            // ==================== FULL SECURITY CAMERA VOICE COMMANDS ====================
            is VoiceCommandParser.Action.OpenCamera -> {
                launchCameraWithVoiceAction(action.devId, voiceAction = null)
                    ?: (false to "Kamera belum ditemukan di rumah Anda.")
            }
            is VoiceCommandParser.Action.CameraLiveAction -> {
                val actionExtra = when (action.type) {
                    VoiceCommandParser.CameraLiveActionType.OPEN -> null
                    VoiceCommandParser.CameraLiveActionType.SNAPSHOT -> "snapshot"
                    VoiceCommandParser.CameraLiveActionType.RECORD -> "record"
                    VoiceCommandParser.CameraLiveActionType.TALK -> "talk"
                    VoiceCommandParser.CameraLiveActionType.UNMUTE -> "unmute"
                    VoiceCommandParser.CameraLiveActionType.MUTE -> "mute"
                }
                val reply = when (action.type) {
                    VoiceCommandParser.CameraLiveActionType.OPEN -> "Membuka tayangan langsung ${action.deviceName}."
                    VoiceCommandParser.CameraLiveActionType.SNAPSHOT -> "Membuka ${action.deviceName} dan langsung mengambil foto."
                    VoiceCommandParser.CameraLiveActionType.RECORD -> "Membuka ${action.deviceName} dan memulai rekaman video."
                    VoiceCommandParser.CameraLiveActionType.TALK -> "Membuka ${action.deviceName} dan mengaktifkan interkom bicara 2 arah."
                    VoiceCommandParser.CameraLiveActionType.UNMUTE -> "Membuka ${action.deviceName} dan menyalakan suara kamera."
                    VoiceCommandParser.CameraLiveActionType.MUTE -> "Membuka ${action.deviceName} dan membisukan suara kamera."
                }
                launchCameraWithVoiceAction(action.devId, actionExtra, reply)
                    ?: (false to "Kamera belum ditemukan di rumah Anda.")
            }
            is VoiceCommandParser.Action.CameraPtz -> {
                val (ptzCode, dirLabel) = when (action.direction) {
                    VoiceCommandParser.PtzDirection.UP -> "0" to "ke atas"
                    VoiceCommandParser.PtzDirection.DOWN -> "4" to "ke bawah"
                    VoiceCommandParser.PtzDirection.LEFT -> "6" to "ke kiri"
                    VoiceCommandParser.PtzDirection.RIGHT -> "2" to "ke kanan"
                    VoiceCommandParser.PtzDirection.STOP -> "" to "berhenti"
                }
                if (action.direction == VoiceCommandParser.PtzDirection.STOP) {
                    publishDirectVoiceDp(action.devId, listOf("ptz_stop"), "116", true)
                    true to "Siap, pergerakan lensa ${action.deviceName} dihentikan."
                } else {
                    publishDirectVoiceDp(action.devId, listOf("ptz_control"), "119", ptzCode)
                    binding.root.postDelayed({
                        publishDirectVoiceDp(action.devId, listOf("ptz_stop"), "116", true)
                    }, 700L)
                    true to "Siap, menggeser lensa ${action.deviceName} $dirLabel."
                }
            }
            is VoiceCommandParser.Action.CameraMotionDetection -> {
                publishDirectVoiceDp(action.devId, listOf("motion_switch"), "134", action.enabled)
                val st = if (action.enabled) "diaktifkan" else "dimatikan"
                true to "Siap, deteksi gerakan pada ${action.deviceName} telah $st."
            }
            is VoiceCommandParser.Action.CameraAutoTracking -> {
                publishDirectVoiceDp(action.devId, listOf("motion_tracking"), "161", action.enabled)
                val st = if (action.enabled) "diaktifkan" else "dimatikan"
                true to "Siap, pelacakan objek otomatis pada ${action.deviceName} telah $st."
            }
            is VoiceCommandParser.Action.CameraNightVision -> {
                publishDirectVoiceDp(action.devId, listOf("basic_nightvision"), "108", action.modeCode)
                true to "Siap, mode malam inframerah ${action.deviceName} diatur ke ${action.modeLabel}."
            }
            is VoiceCommandParser.Action.CameraPrivacyMode -> {
                publishDirectVoiceDp(action.devId, listOf("basic_private"), "105", action.enabled)
                true to if (action.enabled) {
                    "Siap, mode privasi ${action.deviceName} diaktifkan (lensa ditutup)."
                } else {
                    "Siap, mode privasi ${action.deviceName} dimatikan (lensa dibuka)."
                }
            }
            is VoiceCommandParser.Action.CameraFlipScreen -> {
                publishDirectVoiceDp(action.devId, listOf("basic_flip"), "103", action.enabled)
                val st = if (action.enabled) "diaktifkan" else "dimatikan"
                true to "Siap, putar layar 180 derajat pada ${action.deviceName} telah $st."
            }
            is VoiceCommandParser.Action.CameraSiren -> {
                publishDirectVoiceDp(action.devId, listOf("siren_switch"), "159", action.enabled)
                true to if (action.enabled) {
                    "Sirene alarm pada ${action.deviceName} dibunyikan!"
                } else {
                    "Siap, sirene alarm ${action.deviceName} dimatikan."
                }
            }

            // ==================== SMART LIGHTING VOICE COMMANDS ====================
            is VoiceCommandParser.Action.SetLightBrightness -> {
                TuyaDeviceRepository.setAllChannels(action.devId, true)
                allDevicesList = allDevicesList.map { d ->
                    if (d.devId == action.devId) d.copy(isSwitchOn = true) else d.copy()
                }
                applyRoomFilter()
                updateHomeDashboardMetrics()
                publishDirectVoiceDp(action.devId, listOf("switch_led", "switch_1"), "20", true)
                publishDirectVoiceDp(action.devId, listOf("bright_value_v2", "bright_value"), "22", (action.percent * 10).coerceIn(10, 1000))
                true to "Siap, kecerahan ${action.deviceName} diatur ke ${action.percent}%."
            }
            is VoiceCommandParser.Action.SetLightColor -> {
                TuyaDeviceRepository.setAllChannels(action.devId, true)
                allDevicesList = allDevicesList.map { d ->
                    if (d.devId == action.devId) d.copy(isSwitchOn = true) else d.copy()
                }
                applyRoomFilter()
                updateHomeDashboardMetrics()
                publishDirectVoiceDp(action.devId, listOf("switch_led", "switch_1"), "20", true)
                if (action.hue != null) {
                    val hsvHex = String.format(java.util.Locale.US, "%04x%04x%04x", action.hue.toInt().coerceIn(0, 360), 900, 900)
                    publishDirectVoiceDp(action.devId, listOf("work_mode"), "21", "colour")
                    publishDirectVoiceDp(action.devId, listOf("colour_data_v2", "colour_data"), "24", hsvHex)
                } else if (action.cctProgress != null) {
                    publishDirectVoiceDp(action.devId, listOf("work_mode"), "21", "white")
                    publishDirectVoiceDp(action.devId, listOf("temp_value_v2", "temp_value"), "23", (action.cctProgress * 10).coerceIn(0, 1000))
                }
                true to "Siap, warna ${action.deviceName} diubah menjadi ${action.colorLabel}."
            }

            // ==================== SCENES, SCHEDULES, APP FEATURES, HELP ====================
            VoiceCommandParser.Action.RunSleepScene -> {
                runSleepScenario()
                true to "Mode Tidur diaktifkan. Perangkat ruang tamu dan dapur dimatikan."
            }
            VoiceCommandParser.Action.RunLeaveScene -> {
                toggleAllNonCameraDevices(false)
                true to "Mode Keluar diaktifkan. Semua saklar berhasil dimatikan."
            }
            VoiceCommandParser.Action.RunHomeScene -> {
                toggleAllNonCameraDevices(true)
                true to "Selamat datang kembali. Perangkat utama rumah dinyalakan."
            }
            VoiceCommandParser.Action.RunEnergyAudit -> {
                val activeSwitches = allDevicesList.count { dev ->
                    !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) && dev.isSwitchOn
                }
                if (activeSwitches > 0) {
                    toggleAllNonCameraDevices(false)
                    true to "Audit energi selesai: $activeSwitches saklar aktif telah dimatikan untuk menghemat listrik."
                } else {
                    true to "Audit energi: semua saklar sudah dalam kondisi hemat daya."
                }
            }
            VoiceCommandParser.Action.CheckStatus -> {
                val online = allDevicesList.count { it.isOnline }
                val activeSwitches = allDevicesList.count { dev ->
                    !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) && dev.isSwitchOn
                }
                val activeSchedules = SmartScheduleStore.loadAll(this).count { it.enabled }
                true to "Saat ini $online dari ${allDevicesList.size} perangkat online, $activeSwitches saklar menyala, dan $activeSchedules jadwal aktif."
            }
            VoiceCommandParser.Action.OpenAddDevice -> {
                voiceAssistant.dismissIfShowing()
                openPairingScreen()
                true to "Membuka halaman tambah perangkat baru."
            }
            is VoiceCommandParser.Action.OpenSchedule -> {
                voiceAssistant.dismissIfShowing()
                val controllableDevices = allDevicesList
                    .filter { dev -> !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) }
                    .map { dev -> Triple(dev.devId, dev.name, TuyaDeviceClassifier.resolveChannelCount(dev.devId, dev.name, dev.category)) }
                scheduleEditorLauncher.launch(
                    ScheduleTimeActivity.createIntent(
                        context = this,
                        availableDevices = controllableDevices,
                        presetHour = action.hour,
                        presetMinute = action.minute,
                        presetTurnOn = action.turnOn
                    )
                )
                val timeHint = if (action.hour != null) {
                    " jam ${String.format(java.util.Locale.US, "%02d:%02d", action.hour, action.minute ?: 0)}"
                } else ""
                true to "Membuka pengaturan jadwal$timeHint."
            }
            is VoiceCommandParser.Action.ChangeLanguage -> {
                voiceAssistant.dismissIfShowing()
                if (action.langCode != null) {
                    val lang = AppLanguageManager.getLanguageByCode(action.langCode)
                    AppLanguageManager.setLanguage(this, lang.code)
                    recreate()
                    true to "Bahasa aplikasi diubah ke ${lang.nativeName}."
                } else {
                    startActivity(Intent(this, AppLanguageActivity::class.java))
                    true to "Membuka pengaturan Bahasa Aplikasi."
                }
            }
            VoiceCommandParser.Action.OpenRoomManagement -> {
                voiceAssistant.dismissIfShowing()
                ProfileDialogsHelper.showFamilyManagementDialog(this)
                true to "Membuka Manajemen Keluarga dan Ruangan."
            }
            VoiceCommandParser.Action.ShowCapabilities -> {
                true to "Saya bisa mengontrol Kamera (geser kiri/kanan/atas/bawah, deteksi gerakan, pelacakan otomatis, mode malam, privasi, sirene, foto, rekam, interkom), Saklar 1-3, Warna & Kecerahan Lampu, Ruangan, Jadwal, dan Bahasa."
            }
            is VoiceCommandParser.Action.Unknown -> {
                false to "Belum dikenali. Coba: \"Geser kamera ke kiri\", \"Aktifkan deteksi gerakan\", \"Ambil foto kamera\", \"Nyalakan switch 1\", atau \"Bisa apa saja\"."
            }
        }
    }

    private fun launchCameraWithVoiceAction(
        camDevId: String,
        voiceAction: String?,
        customReply: String? = null
    ): Pair<Boolean, String>? {
        val cam = allDevicesList.firstOrNull { it.devId == camDevId } ?: return null
        voiceAssistant.dismissIfShowing()
        val intent = Intent(this, CameraControlActivity::class.java).apply {
            putExtra("DEV_ID", cam.devId)
            putExtra("DEV_NAME", cam.name)
            putExtra("DEVICE_CATEGORY", cam.category)
            putExtra("PRODUCT_ID", cam.productId)
            putExtra("IS_ONLINE", cam.isOnline)
            putExtra("IS_ON", cam.isSwitchOn)
            if (voiceAction != null) {
                putExtra(CameraControlActivity.EXTRA_VOICE_CAMERA_ACTION, voiceAction)
            }
        }
        startActivity(intent)
        return true to (customReply ?: "Membuka tayangan langsung ${cam.name}.")
    }

    private fun publishDirectVoiceDp(devId: String, dpCodes: List<String>, fallbackDpId: String, value: Any) {
        if (!ThinkTechApp.isSdkInitialized || devId.startsWith("demo_")) return
        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            var resolvedDpId = fallbackDpId
            val schemaMap = devBean?.schemaMap
            if (schemaMap != null) {
                for (code in dpCodes) {
                    val found = schemaMap.values.firstOrNull { it.code.equals(code, ignoreCase = true) }
                    if (found?.id != null) {
                        resolvedDpId = found.id
                        break
                    }
                }
            }
            val payload = JSONObject().apply { put(resolvedDpId, value) }.toJSONString()
            ThingHomeSdk.newDeviceInstance(devId).publishDps(payload, object : IResultCallback {
                override fun onError(code: String?, error: String?) {}
                override fun onSuccess() {}
            })
        } catch (_: Throwable) {}
    }

    private fun showAiCommandDialog() {
        voiceAssistant.openAssistant(autoListen = true)
    }

    private fun toggleAllNonCameraDevices(targetState: Boolean) {
        allDevicesList = allDevicesList.map { dev ->
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            if (!isCam) {
                TuyaDeviceRepository.setAllChannels(dev.devId, targetState)
                dev.copy(isSwitchOn = targetState)
            } else {
                dev.copy()
            }
        }
        applyRoomFilter()
        updateHomeDashboardMetrics()
        allDevicesList.forEach { dev ->
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            if (!isCam) {
                deviceRepo.toggleDevicePower(dev.devId, targetState, {}, { _, _ -> })
            }
        }
    }

    private fun runSleepScenario() {
        allDevicesList = allDevicesList.map { dev ->
            val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
            if (!isCam && !dev.name.contains("Bedroom", ignoreCase = true) && !dev.room.contains("Kamar", ignoreCase = true)) {
                TuyaDeviceRepository.setAllChannels(dev.devId, false)
                dev.copy(isSwitchOn = false)
            } else {
                dev.copy()
            }
        }
        applyRoomFilter()
        updateHomeDashboardMetrics()
        allDevicesList
            .filter { dev ->
                val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
                !isCam && !dev.name.contains("Bedroom", ignoreCase = true) && !dev.room.contains("Kamar", ignoreCase = true)
            }
            .forEach { dev ->
                deviceRepo.toggleDevicePower(dev.devId, false, {}, { _, _ -> })
            }
    }

    private fun setupScenarioListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.btnSmartFabAdd.outlineSpotShadowColor = 0x663B5BFF
            binding.btnSmartFabAdd.outlineAmbientShadowColor = 0x263B5BFF
        }

        // Sub-Tab Switcher: Otomasi | Sekali Sentuh
        binding.tabSmartAutomation.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectSmartSubTab(automationMode = true)
        }
        binding.tabSmartTapToRun.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectSmartSubTab(automationMode = false)
        }

        // Header & FAB Schedule Creation Entry Points
        val openNewSchedule: (View) -> Unit = { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            launchScheduleEditor(scheduleId = null)
        }
        binding.btnSmartFabAdd.setOnClickListener(openNewSchedule)
        binding.btnSmartAddHeader.setOnClickListener(openNewSchedule)
        binding.btnSmartAddScheduleInline.setOnClickListener(openNewSchedule)
        binding.btnSmartLogHeader.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectSmartSubTab(automationMode = true)
            val activeCount = SmartScheduleStore.loadAll(this).count { s -> s.enabled }
            Toast.makeText(this, "$activeCount jadwal otomasi cloud sedang aktif", Toast.LENGTH_SHORT).show()
        }

        // 6 Flagship Gradient Tap-to-Run Cards with Live Spinner Feedback
        binding.btnRunSceneLightsOn.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneLightsOnArrow, binding.pbSceneLightsOn) {
                toggleAllNonCameraDevices(true)
                Toast.makeText(this, "Lampu Cepat ON — seluruh saklar & lampu dinyalakan.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSceneSleep.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneSleepArrow, binding.pbSceneSleep) {
                runSleepScenario()
                Toast.makeText(this, "Persiapan Tidur aktif — perangkat ruang tamu & dapur dimatikan.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSceneHome.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneHomeArrow, binding.pbSceneHome) {
                toggleAllNonCameraDevices(true)
                Toast.makeText(this, "Pulang ke Rumah — perangkat utama dinyalakan.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSceneFocus.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneFocusArrow, binding.pbSceneFocus) {
                toggleAllNonCameraDevices(true)
                Toast.makeText(this, "Mode Produktif — pencahayaan kerja dinyalakan penuh.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSceneLeave.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneLeaveArrow, binding.pbSceneLeave) {
                toggleAllNonCameraDevices(false)
                Toast.makeText(this, "Tinggalkan Rumah — seluruh saklar dimatikan.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRunSceneEnergy.setOnClickListener { card ->
            runTapToRunCard(card, binding.ivSceneEnergyArrow, binding.pbSceneEnergy) {
                val activeSwitches = allDevicesList.count { dev ->
                    !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) && dev.isSwitchOn
                }
                ProfileDialogsHelper.showTuyaConfirmDialog(
                    this,
                    title = "Audit Hemat Energi",
                    message = if (activeSwitches > 0) {
                        "Saat ini ada $activeSwitches perangkat saklar sedang menyala. Matikan seluruh saklar yang tidak dipakai untuk menghemat listrik?"
                    } else {
                        "Hebat! Seluruh saklar dalam mode hemat daya (standby). Tidak ada pemborosan daya terdeteksi."
                    },
                    confirmText = if (activeSwitches > 0) "Matikan Semua" else "Tutup",
                    confirmColor = ContextCompat.getColor(this, R.color.harmony_blue)
                ) {
                    if (activeSwitches > 0) {
                        toggleAllNonCameraDevices(false)
                        Toast.makeText(this, "Mode Hemat Energi aktif — semua saklar dimatikan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        selectSmartSubTab(automationMode = false)
        refreshSmartAutomations()
    }

    private fun runTapToRunCard(
        card: View,
        arrowView: View,
        spinnerView: View,
        action: () -> Unit
    ) {
        card.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(85L).withEndAction {
            card.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
        }.start()

        arrowView.visibility = View.GONE
        spinnerView.visibility = View.VISIBLE
        card.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            spinnerView.visibility = View.GONE
            arrowView.visibility = View.VISIBLE
            action()
        }, 620L)
    }

    private fun selectSmartSubTab(automationMode: Boolean) {
        isSmartAutomationSubTab = automationMode
        binding.panelSmartAutomation.visibility = if (automationMode) View.VISIBLE else View.GONE
        binding.panelSmartTapToRun.visibility = if (automationMode) View.GONE else View.VISIBLE

        val activeBg = R.drawable.bg_lamp_tab_active
        val darkInk = Color.parseColor("#2A303D")
        if (automationMode) {
            binding.tabSmartAutomation.setBackgroundResource(activeBg)
            binding.tabSmartAutomation.setTextColor(Color.WHITE)
            binding.tabSmartTapToRun.background = null
            binding.tabSmartTapToRun.setTextColor(darkInk)
        } else {
            binding.tabSmartTapToRun.setBackgroundResource(activeBg)
            binding.tabSmartTapToRun.setTextColor(Color.WHITE)
            binding.tabSmartAutomation.background = null
            binding.tabSmartAutomation.setTextColor(darkInk)
        }
    }

    private fun refreshSmartAutomations() {
        val schedules = SmartScheduleStore.loadAll(this)
        val activeCount = schedules.count { it.enabled }
        binding.tvSmartSubtitle.text = "6 pintasan instan • $activeCount jadwal aktif"
        binding.tvSmartScheduleCountBadge.text = "$activeCount Aktif"

        val container = binding.layoutSmartScheduleList
        container.removeAllViews()
        binding.layoutSmartScheduleEmpty.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE

        schedules.forEach { schedule ->
            val row = layoutInflater.inflate(R.layout.item_smart_schedule, container, false)
            bindScheduleRow(row, schedule) {
                launchScheduleEditor(schedule.id)
            }
            container.addView(row)
        }
        AppLanguageManager.localizeActivity(this)
    }

    private fun bindScheduleRow(row: View, schedule: SmartSchedule, onRowClick: () -> Unit) {
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
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = schedule.enabled
        sw.setOnCheckedChangeListener { _, isChecked ->
            SmartScheduleStore.setEnabled(this, schedule.id, isChecked)
            refreshSmartAutomations()
        }

        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onRowClick()
        }
    }

    private fun launchScheduleEditor(scheduleId: String?) {
        val controllableDevices = allDevicesList
            .filter { dev -> !TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId) }
            .map { dev ->
                val chCount = TuyaDeviceClassifier.resolveChannelCount(dev.devId, dev.name, dev.category)
                Triple(dev.devId, dev.name, chCount)
            }
        val intent = ScheduleTimeActivity.createIntent(
            context = this,
            scheduleId = scheduleId,
            availableDevices = controllableDevices
        )
        scheduleEditorLauncher.launch(intent)
    }

    private fun setupExploreListeners() {
        binding.cardExploreProtocol.setOnClickListener {
            ProfileDialogsHelper.showTuyaInfoDialog(
                this,
                title = "ThinkTech Multi-Protocol IoT",
                message = "Mendukung pairing Bluetooth LE, Wi-Fi 2.4 GHz, Zigbee 3.0, serta standar Matter melalui Tuya Smart Life SDK v7.8.0."
            )
        }

        binding.cardExploreVoice.setOnClickListener {
            ProfileDialogsHelper.showGoogleHomeDialog(this)
        }
    }

    private fun setupProfileListeners() {
        binding.btnQrScanner.setOnClickListener {
            ProfileDialogsHelper.showQrScannerDialog(this)
        }

        binding.btnSettingsGear.setOnClickListener {
            ProfileDialogsHelper.showAppSettingsDialog(this)
        }

        binding.layoutProfileHeader.setOnClickListener {
            ProfileDialogsHelper.showUserProfileDialog(this, authRepo) {
                setupUserInfo()
            }
        }

        binding.tvCurrentAppLanguage.text = AppLanguageManager.getCurrentLanguage(this).nativeName

        binding.menuAppLanguage.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, AppLanguageActivity::class.java))
        }

        binding.menuFamilyManagement.setOnClickListener {
            ProfileDialogsHelper.showFamilyManagementDialog(this)
        }

        binding.menuMessageCenter.setOnClickListener {
            ProfileDialogsHelper.showMessageCenterDialog(this)
        }

        binding.menuFaqFeedback.setOnClickListener {
            ProfileDialogsHelper.showFaqFeedbackDialog(this)
        }

        binding.menuThirdParty.setOnClickListener {
            ProfileDialogsHelper.showThirdPartyDialog(this)
        }

        binding.menuAndroidAuto.setOnClickListener {
            ProfileDialogsHelper.showAndroidAutoDialog(this)
        }

        binding.menuAiWebVersion.setOnClickListener {
            ProfileDialogsHelper.showAiWebVersionDialog(this)
        }

        binding.menuGoogleHome.setOnClickListener {
            ProfileDialogsHelper.showGoogleHomeDialog(this)
        }

        binding.btnLogoutProfile.setOnClickListener {
            ProfileDialogsHelper.showLogoutConfirmDialog(this) {
                authRepo.logout(
                    onSuccess = {
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onError = { _, error ->
                        Toast.makeText(this, "Logout error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun setupGeneralListeners() {
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.harmony_blue))
        binding.swipeRefresh.setOnRefreshListener {
            loadDevices()
        }

        binding.btnTopAddDevice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showTopAddMenu(it)
        }

        binding.layoutHomeSelector.setOnClickListener {
            ProfileDialogsHelper.showFamilyManagementDialog(this)
        }
    }

    private fun showTopAddMenu(anchor: View) {
        if (topMenuPopup?.isShowing == true) {
            topMenuPopup?.dismiss()
            return
        }

        val popupBinding = PopupHomeAddMenuBinding.inflate(layoutInflater)
        val density = resources.displayMetrics.density
        val popupWidth = (252 * density).toInt()

        val popupWindow = PopupWindow(
            popupBinding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        popupBinding.itemAddDevice.setOnClickListener {
            popupWindow.dismiss()
            openPairingScreen()
        }

        popupBinding.itemCreateAutomation.setOnClickListener {
            popupWindow.dismiss()
            ProfileDialogsHelper.showTuyaConfirmDialog(
                this,
                title = "Buat Otomasi",
                message = "Buat skenario otomatisasi cerdas baru untuk menghubungkan perangkat IoT dengan jadwal waktu, kondisi cuaca, atau trigger sensor.",
                confirmText = "Buka Skenario",
                confirmColor = ContextCompat.getColor(this, R.color.harmony_blue)
            ) {
                binding.bottomNavigation.selectedItemId = R.id.nav_smart
            }
        }

        popupBinding.itemCreateTapToRun.setOnClickListener {
            popupWindow.dismiss()
            ProfileDialogsHelper.showTuyaConfirmDialog(
                this,
                title = "Buat Pintasan",
                message = "Buat pintasan satu sentuhan untuk menjalankan skenario perangkat secara instan.",
                confirmText = "Buka Skenario",
                confirmColor = ContextCompat.getColor(this, R.color.harmony_blue)
            ) {
                binding.bottomNavigation.selectedItemId = R.id.nav_smart
            }
        }

        popupBinding.itemAddCard.setOnClickListener {
            popupWindow.dismiss()
            Toast.makeText(this, "Kartu Status Rumah & Layanan AI sudah aktif di Beranda", Toast.LENGTH_SHORT).show()
        }

        popupBinding.itemScan.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, PairDeviceActivity::class.java).apply {
                putExtra("HOME_ID", deviceRepo.getCurrentHomeId())
                putExtra("AUTO_SCAN", true)
            }
            startActivity(intent)
        }

        topMenuPopup = popupWindow

        val yOffset = (6 * density).toInt()
        val xOffset = (8 * density).toInt()
        popupWindow.showAsDropDown(anchor, xOffset, yOffset, Gravity.END)
        dimBehind(popupWindow, 0.24f)
    }

    private fun dimBehind(popupWindow: PopupWindow, dimAmount: Float) {
        try {
            val container = popupWindow.contentView.rootView
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
            val p = container.layoutParams as? WindowManager.LayoutParams ?: return
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            p.dimAmount = dimAmount
            wm.updateViewLayout(container, p)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heroLiveDotAnimator?.cancel()
        heroLiveDotAnimator = null
        voiceAssistant.release()
        topMenuPopup?.dismiss()
        activeDeviceListeners.forEach { (devId, _) ->
            try {
                deviceRepo.unregisterDeviceListener(devId)
            } catch (_: Throwable) {
                // Ignore
            }
        }
        activeDeviceListeners.clear()
    }

    private fun openPairingScreen() {
        val intent = Intent(this, PairDeviceActivity::class.java).apply {
            putExtra("HOME_ID", deviceRepo.getCurrentHomeId())
        }
        startActivity(intent)
    }

    private fun loadDevices() {
        ThinkTechApp.ensureP2pUserSessionInitialized()
        binding.swipeRefresh.isRefreshing = true
        deviceRepo.loadHomeAndDevices(
            onSuccess = { _, devices ->
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    val uniqueDevices = devices.distinctBy { it.devId }
                    allDevicesList = uniqueDevices

                    updateHomeDashboardMetrics()
                    applyRoomFilter()
                    AppLanguageManager.localizeActivity(this@MainActivity)

                    uniqueDevices.forEach { dev ->
                        val isCam = TuyaDeviceClassifier.isCameraDevice(dev.devId, dev.name, dev.category, dev.productId)
                        if (!isCam && !dev.devId.startsWith("demo_") && !activeDeviceListeners.containsKey(dev.devId)) {
                            val listener = object : IDevListener {
                                override fun onDpUpdate(devId: String?, dpStr: String?) {
                                    val targetDevId = devId ?: dev.devId
                                    runOnUiThread {
                                        try {
                                            val dpsJson = JSON.parseObject(dpStr) ?: return@runOnUiThread
                                            for (i in 0 until 3) {
                                                val dpKey = TuyaDeviceRepository.resolveDpForChannel(targetDevId, i)
                                                val st = dpsJson.getBoolean(dpKey)
                                                    ?: dpsJson.getBoolean((i + 1).toString())
                                                    ?: dpsJson.getBoolean((101 + i).toString())
                                                if (st != null) {
                                                    TuyaDeviceRepository.setChannel(targetDevId, i, st)
                                                }
                                            }
                                            val chStates = TuyaDeviceRepository.getChannels(targetDevId)
                                            val newSwitchState = chStates.any { it }

                                            allDevicesList = allDevicesList.map { d ->
                                                if (d.devId == targetDevId) d.copy(isSwitchOn = newSwitchState) else d.copy()
                                            }
                                            applyRoomFilter()
                                            updateHomeDashboardMetrics()
                                        } catch (_: Exception) {
                                            // Ignore edge case JSON parsing
                                        }
                                    }
                                }

                                override fun onRemoved(devId: String?) {
                                    runOnUiThread { loadDevices() }
                                }

                                override fun onStatusChanged(devId: String?, online: Boolean) {
                                    val targetDevId = devId ?: dev.devId
                                    runOnUiThread {
                                        allDevicesList = allDevicesList.map { d ->
                                            if (d.devId == targetDevId) d.copy(isOnline = online) else d.copy()
                                        }
                                        applyRoomFilter()
                                        updateHomeDashboardMetrics()
                                    }
                                }

                                override fun onNetworkStatusChanged(devId: String?, status: Boolean) {}
                                override fun onDevInfoUpdate(devId: String?) {}
                            }
                            deviceRepo.registerDeviceListener(dev.devId, listener)
                            activeDeviceListeners[dev.devId] = listener
                        }
                    }
                }
            },
            onError = { code, error ->
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    updateHomeDashboardMetrics()
                    Toast.makeText(this, "Gagal memuat perangkat ($code): $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacingPx: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            outRect.left = column * spacingPx / spanCount
            outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
            if (position >= spanCount) {
                outRect.top = spacingPx
            }
        }
    }
}
