package com.thinktech.smarthome.data

import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.data.model.SmartDeviceItem

class TuyaDeviceRepository {

    companion object {
        private const val TAG = "TuyaDeviceRepo"
        private const val PREFS_DEVICES = "thinktech_devices_prefs"
        private const val KEY_CUSTOM_DEVICES = "custom_devices_json"
        private const val KEY_DELETED_DEVICES = "deleted_device_ids"
        private const val KEY_HOME_ID = "current_tuya_home_id"

        val customDevices = mutableListOf<SmartDeviceItem>()
        val deletedDeviceIds = mutableSetOf<String>()
        val channelStates = mutableMapOf<String, BooleanArray>()
        val channelNames = mutableMapOf<String, Array<String>>()
        private val recentlyPairedAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private const val PAIRING_PROPAGATION_GRACE_MS = 45_000L
        var currentHomeId: Long = 0L
        private var isInitializedFromPrefs = false

        // shortcut: keeps locally paired physical IDs for 45s while Tuya Cloud propagates home detail, replace with MQTT home-device diff listener if multi-phone concurrent pairing is needed
        internal fun staleCustomIdsToPrune(
            customIds: Collection<String>,
            cloudSdkIds: Set<String>,
            pairedTimestampsMs: Map<String, Long>,
            nowMs: Long,
            graceMs: Long = PAIRING_PROPAGATION_GRACE_MS
        ): Set<String> {
            return customIds.filterTo(mutableSetOf()) { id ->
                if (id.isBlank() || id.startsWith("demo_")) return@filterTo false
                if (id in cloudSdkIds) return@filterTo true
                val pairedAt = pairedTimestampsMs[id] ?: 0L
                (nowMs - pairedAt) > graceMs
            }
        }

        fun evictStaleCustomDevice(devId: String) {
            initFromPrefsIfNeeded()
            if (devId.isBlank()) return
            recentlyPairedAtMs.remove(devId)
            if (customDevices.removeAll { it.devId == devId }) {
                saveToPrefs()
            }
        }

        fun initFromPrefsIfNeeded() {
            if (isInitializedFromPrefs) return
            try {
                val context = ThinkTechApp.instance
                val prefs = context.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
                val deleted = prefs.getStringSet(KEY_DELETED_DEVICES, null)
                if (deleted != null) {
                    deletedDeviceIds.addAll(deleted)
                }
                currentHomeId = prefs.getLong(KEY_HOME_ID, 0L)
                val json = prefs.getString(KEY_CUSTOM_DEVICES, null)
                if (!json.isNullOrEmpty()) {
                    val list = com.alibaba.fastjson.JSON.parseArray(json, SmartDeviceItem::class.java)
                    if (list != null) {
                        customDevices.clear()
                        for (item in list) {
                            val id = item?.devId
                            if (!id.isNullOrBlank() && !id.startsWith("demo_") && id !in deletedDeviceIds) {
                                customDevices.add(item)
                            }
                        }
                    }
                }
                isInitializedFromPrefs = true
            } catch (e: Throwable) {
                Log.e(TAG, "Error initializing from prefs: ${e.message}")
            }
        }

        fun ensureHomeId(onReady: (Long) -> Unit, onError: (String, String) -> Unit) {
            initFromPrefsIfNeeded()
            if (currentHomeId != 0L && currentHomeId != 99999L) {
                onReady(currentHomeId)
                return
            }
            if (!ThinkTechApp.isSdkInitialized) {
                onError("SDK_NOT_INITIALIZED", "Tuya SDK belum siap")
                return
            }

            val homeManager = try {
                ThingHomeSdk.getHomeManagerInstance()
            } catch (t: Throwable) {
                null
            }

            if (homeManager == null) {
                onError("NO_HOME_MANAGER", "Home Manager belum siap. Pastikan akun sudah masuk.")
                return
            }

            try {
                homeManager.queryHomeList(object : IThingGetHomeListCallback {
                    override fun onSuccess(homeList: MutableList<HomeBean>?) {
                        if (!homeList.isNullOrEmpty()) {
                            val hId = homeList.first().homeId
                            currentHomeId = hId
                            try {
                                val prefs = ThinkTechApp.instance.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
                                prefs.edit().putLong(KEY_HOME_ID, hId).apply()
                            } catch (e: Throwable) {}
                            onReady(hId)
                        } else {
                            try {
                                homeManager.createHome(
                                    "ThinkTech Home",
                                    106.8456,
                                    -6.2088,
                                    "Jakarta",
                                    listOf("Living Room", "Bedroom", "Kitchen"),
                                    object : IThingHomeResultCallback {
                                        override fun onSuccess(bean: HomeBean?) {
                                            if (bean != null) {
                                                currentHomeId = bean.homeId
                                                try {
                                                    val prefs = ThinkTechApp.instance.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
                                                    prefs.edit().putLong(KEY_HOME_ID, bean.homeId).apply()
                                                } catch (e: Throwable) {}
                                                onReady(bean.homeId)
                                            } else {
                                                onError("CREATE_HOME_NULL", "Gagal membuat rumah")
                                            }
                                        }
                                        override fun onError(code: String, error: String) {
                                            onError(code, error)
                                        }
                                    }
                                )
                            } catch (t: Throwable) {
                                onError("CREATE_HOME_EX", t.message ?: "Error creating home")
                            }
                        }
                    }
                    override fun onError(code: String, error: String) {
                        onError(code, error)
                    }
                })
            } catch (t: Throwable) {
                onError("QUERY_HOME_EX", t.message ?: "Error querying home list")
            }
        }

        fun saveToPrefs() {
            try {
                val context = ThinkTechApp.instance
                val prefs = context.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putStringSet(KEY_DELETED_DEVICES, HashSet(deletedDeviceIds))
                    .putString(KEY_CUSTOM_DEVICES, com.alibaba.fastjson.JSON.toJSONString(customDevices))
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to prefs: ${e.message}")
            }
        }

        fun getChannels(devId: String, defaultCount: Int = 3): BooleanArray {
            initFromPrefsIfNeeded()
            return channelStates.getOrPut(devId) {
                BooleanArray(defaultCount) { true }
            }
        }

        fun getChannelNames(devId: String, defaultNames: Array<String>): Array<String> {
            initFromPrefsIfNeeded()
            return channelNames.getOrPut(devId) { defaultNames }
        }

        fun setChannelName(devId: String, index: Int, name: String) {
            initFromPrefsIfNeeded()
            val names = channelNames[devId] ?: return
            if (index in names.indices) {
                names[index] = name
            }
        }

        fun setChannel(devId: String, channelIndex: Int, state: Boolean) {
            initFromPrefsIfNeeded()
            val arr = getChannels(devId)
            if (channelIndex in arr.indices) {
                arr[channelIndex] = state
                channelStates[devId] = arr
            }
            val anyOn = arr.any { it }
            val idx = customDevices.indexOfFirst { it.devId == devId }
            if (idx != -1) {
                customDevices[idx] = customDevices[idx].copy(isSwitchOn = anyOn)
                saveToPrefs()
            }
        }

        fun setAllChannels(devId: String, state: Boolean, count: Int = 3) {
            initFromPrefsIfNeeded()
            val arr = BooleanArray(count) { state }
            channelStates[devId] = arr
            val idx = customDevices.indexOfFirst { it.devId == devId }
            if (idx != -1) {
                customDevices[idx] = customDevices[idx].copy(isSwitchOn = state)
                saveToPrefs()
            }
        }

        fun resolveDpForChannel(devId: String, channelIndex: Int): String {
            if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) {
                return (channelIndex + 1).toString()
            }
            try {
                val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
                val dps = devBean?.dps
                val schemaMap = devBean?.schemaMap

                // 1. Check standard 1-based index ("1", "2", "3")
                val standardKey = (channelIndex + 1).toString()
                if (dps?.containsKey(standardKey) == true || schemaMap?.containsKey(standardKey) == true) {
                    return standardKey
                }

                // 2. Check schema dpCode matching switch_1, switch_2, switch_3, switch_led
                val dpCodeTarget = "switch_${channelIndex + 1}"
                if (schemaMap != null) {
                    for ((dpId, schema) in schemaMap) {
                        if (schema.code.equals(dpCodeTarget, ignoreCase = true) ||
                            schema.code.equals("switch${channelIndex + 1}", ignoreCase = true) ||
                            schema.code.equals("switch$channelIndex", ignoreCase = true) ||
                            (channelIndex == 0 && (schema.code.equals("switch_led", ignoreCase = true) || schema.code.equals("switch", ignoreCase = true)))
                        ) {
                            return dpId
                        }
                    }
                }

                // 2b. Check DP "20" for Tuya Smart Bulbs on channel 0
                if (channelIndex == 0 && (dps?.containsKey("20") == true || schemaMap?.containsKey("20") == true)) {
                    return "20"
                }

                // 3. Check 100-series (101, 102, 103) ONLY if device schema actually has it
                val altKey = (101 + channelIndex).toString()
                if (dps?.containsKey(altKey) == true || schemaMap?.containsKey(altKey) == true) {
                    return altKey
                }

                return standardKey
            } catch (e: Exception) {
                return (channelIndex + 1).toString()
            }
        }

        fun addCustomDevice(
            name: String,
            category: String,
            productId: String = "tt_plug_v1",
            explicitDevId: String? = null
        ): SmartDeviceItem {
            initFromPrefsIfNeeded()
            val cleanName = if (name.isBlank() || name.equals("null", true)) "WiFi 3ch switch" else name.replace("\n", " ")
            val assignedDevId = if (!explicitDevId.isNullOrBlank()) explicitDevId else "demo_dev_${System.currentTimeMillis() % 100000}"
            val newDev = SmartDeviceItem(
                devId = assignedDevId,
                name = cleanName,
                isOnline = true,
                isSwitchOn = true,
                productId = productId,
                category = category,
                room = getDeviceRoom(assignedDevId)
            )
            deletedDeviceIds.remove(newDev.devId)
            if (!newDev.devId.startsWith("demo_")) {
                recentlyPairedAtMs[newDev.devId] = System.currentTimeMillis()
            }
            customDevices.removeAll { it.devId == newDev.devId }
            customDevices.add(0, newDev)
            channelStates[newDev.devId] = BooleanArray(3) { true }
            channelNames[newDev.devId] = arrayOf("Switch 1", "Switch 2", "Switch 3")
            saveToPrefs()
            return newDev
        }

        fun renameDevice(devId: String, newName: String) {
            initFromPrefsIfNeeded()
            val idx = customDevices.indexOfFirst { it.devId == devId }
            if (idx != -1) {
                customDevices[idx] = customDevices[idx].copy(name = newName)
                saveToPrefs()
            }
        }

        fun setDeviceRoom(devId: String, roomName: String) {
            initFromPrefsIfNeeded()
            val context = ThinkTechApp.instance
            val prefs = context.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("room_" + devId, roomName).apply()
            val idx = customDevices.indexOfFirst { it.devId == devId }
            if (idx != -1) {
                customDevices[idx] = customDevices[idx].copy(room = roomName)
                saveToPrefs()
            }
        }

        fun getDeviceRoom(devId: String): String {
            initFromPrefsIfNeeded()
            val context = ThinkTechApp.instance
            val prefs = context.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
            return prefs.getString("room_" + devId, null) ?: "Ruang Tamu"
        }

        fun removeDeviceStatic(
            devId: String,
            onSuccess: () -> Unit,
            onError: (code: String, error: String) -> Unit = { _, _ -> }
        ) {
            initFromPrefsIfNeeded()
            if (devId.isNotEmpty()) {
                deletedDeviceIds.add(devId)
                customDevices.removeAll { it.devId == devId }
                channelStates.remove(devId)
                channelNames.remove(devId)
            }
            saveToPrefs()

            if (ThinkTechApp.isSdkInitialized && devId.isNotEmpty() && !devId.startsWith("demo_")) {
                try {
                    val deviceInstance = ThingHomeSdk.newDeviceInstance(devId)
                    deviceInstance.removeDevice(object : com.thingclips.smart.sdk.api.IResultCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "Device $devId successfully removed via Tuya SDK")
                            onSuccess()
                        }

                        override fun onError(code: String, error: String) {
                            Log.w(TAG, "Tuya SDK removeDevice: $code - $error (locally removed)")
                            onSuccess()
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Exception removing device from SDK: ${e.message}")
                    onSuccess()
                }
            } else {
                onSuccess()
            }
        }
    }

    private var currentHomeId: Long = 0
    private val demoDeviceStates = mutableMapOf(
        "demo_switch_01" to true,
        "demo_bulb_02" to false,
        "demo_plug_03" to true
    )

    fun getCurrentHomeId(): Long = currentHomeId

    fun removeDevice(
        devId: String,
        onSuccess: () -> Unit,
        onError: (code: String, error: String) -> Unit = { _, _ -> }
    ) {
        demoDeviceStates.remove(devId)
        removeDeviceStatic(devId, onSuccess, onError)
    }

    fun getDemoDevices(): List<SmartDeviceItem> {
        initFromPrefsIfNeeded()
        val baseList = mutableListOf(
            SmartDeviceItem(
                devId = "demo_switch_01",
                name = "Smart Switch Living Room",
                isOnline = true,
                isSwitchOn = demoDeviceStates["demo_switch_01"] ?: true,
                productId = "tt_switch_v1",
                category = "Saklar Pintar"
            ),
            SmartDeviceItem(
                devId = "demo_bulb_02",
                name = "Smart Bulb Bedroom RGB",
                isOnline = true,
                isSwitchOn = demoDeviceStates["demo_bulb_02"] ?: false,
                productId = "tt_bulb_v1",
                category = "Lampu Pintar"
            ),
            SmartDeviceItem(
                devId = "demo_plug_03",
                name = "Smart Socket Kitchen",
                isOnline = true,
                isSwitchOn = demoDeviceStates["demo_plug_03"] ?: true,
                productId = "tt_plug_v1",
                category = "Stop Kontak Pintar"
            )
        )
        baseList.removeAll { it.devId in deletedDeviceIds }
        val activeCustom = customDevices.filterNot { it.devId in deletedDeviceIds }
        baseList.addAll(0, activeCustom)
        return baseList
    }

    /**
     * Mengambil daftar Home milik user. Jika belum ada, otomatis membuat default Home "ThinkTech Home".
     */
    fun loadHomeAndDevices(
        onSuccess: (home: HomeBean, devices: List<SmartDeviceItem>) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val authRepo = TuyaAuthRepository()
        val isUserLoggedIn = try {
            ThinkTechApp.isSdkInitialized && ThingHomeSdk.getUserInstance()?.isLogin == true
        } catch (e: Throwable) {
            false
        }

        if (!isUserLoggedIn && authRepo.hasSavedCredentials()) {
            Log.d(TAG, "SDK session not active, attempting silent auto-login before loading devices...")
            authRepo.silentAutoLogin(
                onSuccess = {
                    proceedLoadHomeAndDevices(onSuccess, onError)
                },
                onError = { _, _ ->
                    proceedLoadHomeAndDevices(onSuccess, onError)
                }
            )
            return
        }

        proceedLoadHomeAndDevices(onSuccess, onError)
    }

    private fun proceedLoadHomeAndDevices(
        onSuccess: (home: HomeBean, devices: List<SmartDeviceItem>) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val isUserLoggedIn = try {
            ThinkTechApp.isSdkInitialized && ThingHomeSdk.getUserInstance()?.isLogin == true
        } catch (e: Throwable) {
            false
        }

        if (!isUserLoggedIn) {
            val demoHome = HomeBean().apply {
                name = "ThinkTech Demo Home"
                homeId = 99999
            }
            onSuccess(demoHome, getDemoDevices())
            return
        }

        val homeManager = try {
            ThingHomeSdk.getHomeManagerInstance()
        } catch (t: Throwable) {
            null
        }

        if (homeManager == null) {
            val demoHome = HomeBean().apply {
                name = "ThinkTech Home"
                homeId = 99999
            }
            onSuccess(demoHome, getDemoDevices())
            return
        }

        try {
            homeManager.queryHomeList(object : IThingGetHomeListCallback {
                override fun onSuccess(homeList: MutableList<HomeBean>?) {
                    if (homeList.isNullOrEmpty()) {
                        // Buat Home baru jika akun belum memiliki Home
                        createDefaultHome(onSuccess, onError)
                    } else {
                        val activeHome = homeList.first()
                        currentHomeId = activeHome.homeId
                        runCatching {
                            ThinkTechApp.instance.getSharedPreferences(PREFS_DEVICES, android.content.Context.MODE_PRIVATE)
                                .edit().putLong(KEY_HOME_ID, activeHome.homeId).apply()
                        }
                        loadHomeDetail(activeHome.homeId, onSuccess, onError)
                    }
                }

                override fun onError(errorCode: String, error: String) {
                    Log.e(TAG, "queryHomeList error: $errorCode - $error")
                    onError(errorCode, error)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "queryHomeList exception: ${t.message}")
            val demoHome = HomeBean().apply {
                name = "ThinkTech Home"
                homeId = 99999
            }
            onSuccess(demoHome, getDemoDevices())
        }
    }

    private fun createDefaultHome(
        onSuccess: (home: HomeBean, devices: List<SmartDeviceItem>) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        val homeManager = try {
            ThingHomeSdk.getHomeManagerInstance()
        } catch (t: Throwable) {
            null
        }
        if (homeManager == null) {
            onError("NO_HOME_MANAGER", "Home Manager belum siap")
            return
        }

        try {
            // Parameter createHome: (name, lon, lat, geoName, rooms, callback)
            homeManager.createHome(
                "ThinkTech Home",
                106.8456,
                -6.2088,
                "Jakarta",
                listOf("Living Room", "Bedroom", "Kitchen"),
                object : IThingHomeResultCallback {
                    override fun onSuccess(bean: HomeBean?) {
                        if (bean != null) {
                            currentHomeId = bean.homeId
                            loadHomeDetail(bean.homeId, onSuccess, onError)
                        } else {
                            onError("CREATE_HOME_NULL", "Gagal membuat rumah")
                        }
                    }

                    override fun onError(errorCode: String, error: String) {
                        onError(errorCode, error)
                    }
                }
            )
        } catch (t: Throwable) {
            onError("CREATE_HOME_EX", t.message ?: "Error creating home")
        }
    }

    private fun loadHomeDetail(
        homeId: Long,
        onSuccess: (home: HomeBean, devices: List<SmartDeviceItem>) -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        initFromPrefsIfNeeded()
        val homeInstance = try {
            ThingHomeSdk.newHomeInstance(homeId)
        } catch (t: Throwable) {
            null
        }
        if (homeInstance == null) {
            onError("HOME_INSTANCE_NULL", "Gagal memuat instance rumah")
            return
        }

        try {
            homeInstance.getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(homeBean: HomeBean?) {
                    if (homeBean == null) {
                        onSuccess(HomeBean(), emptyList())
                        return
                    }
                    val deviceList = homeBean.deviceList ?: emptyList()
                    val sdkItems = mapDeviceBeansToItems(deviceList)
                        .filterNot { it.devId in deletedDeviceIds }
                        .toMutableList()

                    // Hapus duplikasi atau perangkat fisik lama yang sudah di-unpair / dipindah ke aplikasi Tuya lain
                    val cloudDevIds = deviceList.mapNotNull { it?.devId }.toSet()
                    val sdkDevIds = sdkItems.map { it.devId }.toSet()
                    val idsToPrune = staleCustomIdsToPrune(
                        customIds = customDevices.map { it.devId },
                        cloudSdkIds = cloudDevIds,
                        pairedTimestampsMs = recentlyPairedAtMs,
                        nowMs = System.currentTimeMillis()
                    )
                    if (idsToPrune.isNotEmpty()) {
                        customDevices.removeAll { it.devId in idsToPrune }
                        idsToPrune.forEach { recentlyPairedAtMs.remove(it) }
                        saveToPrefs()
                    }

                    val activeCustom = customDevices.filterNot { 
                        it.devId.isNullOrEmpty() || 
                        it.devId in deletedDeviceIds || 
                        it.devId.startsWith("demo_") ||
                        it.devId in sdkDevIds
                    }
                    sdkItems.addAll(0, activeCustom)
                    val finalUniqueItems = sdkItems.distinctBy { it.devId }
                    onSuccess(homeBean, finalUniqueItems)
                }

                override fun onError(errorCode: String, error: String) {
                    onError(errorCode, error)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "getHomeDetail exception: ${t.message}")
            onError("HOME_DETAIL_EX", t.message ?: "Error getting home detail")
        }
    }

    fun mapDeviceBeansToItems(devices: List<DeviceBean>?): List<SmartDeviceItem> {
        if (devices == null) return emptyList()
        initFromPrefsIfNeeded()
        return devices.mapNotNull { dev ->
            if (dev == null) return@mapNotNull null
            val devId = dev.devId ?: ""
            val dps = try { dev.dps } catch (e: Throwable) { null }

            // Sync any received DPs into channel states if present
            if (dps != null && devId.isNotBlank()) {
                for (i in 0 until 3) {
                    val dpKey = resolveDpForChannel(devId, i)
                    val st = dps[dpKey] as? Boolean
                        ?: dps[(i + 1).toString()] as? Boolean
                        ?: dps[(101 + i).toString()] as? Boolean
                    if (st != null) {
                        setChannel(devId, i, st)
                    }
                }
            }

            // Determine if ANY channel is active
            val chStates = channelStates[devId]
            val isSwitchOn = if (chStates != null) {
                chStates.any { it }
            } else {
                val boolKeys = listOf("1", "2", "3", "4", "20", "101", "102", "103", "104")
                var foundAnyOn = false
                var foundAnyDp = false
                for (key in boolKeys) {
                    val state = dps?.get(key) as? Boolean
                    if (state != null) {
                        foundAnyDp = true
                        if (state) {
                            foundAnyOn = true
                            break
                        }
                    }
                }
                if (foundAnyDp) foundAnyOn else ((dps?.get("1") as? Boolean) ?: (dps?.get("20") as? Boolean) ?: false)
            }

            val onlineStatus = try {
                dev.isOnline
            } catch (e: Throwable) {
                try { dev.getIsOnline() } catch (t: Throwable) { false }
            }

            val classified = TuyaDeviceClassifier.classify(
                rawName = dev.name,
                categoryCode = dev.category,
                productId = dev.productId
            )

            SmartDeviceItem(
                devId = devId,
                name = if (!dev.name.isNullOrBlank() && !dev.name.equals("null", true)) dev.name else classified.displayName,
                iconUrl = dev.iconUrl,
                isOnline = onlineStatus ?: false,
                isSwitchOn = isSwitchOn,
                productId = dev.productId ?: "",
                category = classified.category,
                room = getDeviceRoom(devId)
            )
        }
    }

    /**
     * Mengontrol Saklar / Power ON-OFF perangkat Tuya via Data Point (DP).
     */
    fun toggleDevicePower(
        devId: String,
        targetState: Boolean,
        onSuccess: () -> Unit,
        onError: (code: String, error: String) -> Unit
    ) {
        setAllChannels(devId, targetState)
        if (!ThinkTechApp.isSdkInitialized || devId.startsWith("demo_")) {
            demoDeviceStates[devId] = targetState
            onSuccess()
            return
        }

        if (TuyaDeviceClassifier.isCameraDevice(devId, null, null, null)) {
            onSuccess()
            return
        }

        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val chCount = TuyaDeviceClassifier.resolveChannelCount(devId, devBean?.name, devBean?.category)
            setAllChannels(devId, targetState, chCount)

            val deviceInstance = ThingHomeSdk.newDeviceInstance(devId)
            val dpsJson = JSONObject()
            for (i in 0 until chCount) {
                val dpKey = resolveDpForChannel(devId, i)
                dpsJson[dpKey] = targetState
            }

            Log.i(TAG, "Publishing All Channels to $devId: $dpsJson")
            deviceInstance.publishDps(dpsJson.toJSONString(), object : com.thingclips.smart.sdk.api.IResultCallback {
                override fun onSuccess() {
                    Log.d(TAG, "Device $devId all channels toggled to $targetState")
                    onSuccess()
                }

                override fun onError(code: String, error: String) {
                    Log.e(TAG, "toggleDevicePower error: $code - $error")
                    onError(code, error)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "toggleDevicePower exception: ${e.message}")
            onError("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    /**
     * Mengontrol masing-masing saluran/gang saklar secara independen via DP.
     */
    fun toggleChannelDp(
        devId: String,
        channelIndex: Int,
        targetState: Boolean,
        onSuccess: () -> Unit = {},
        onError: (code: String, error: String) -> Unit = { _, _ -> }
    ) {
        setChannel(devId, channelIndex, targetState)
        if (!ThinkTechApp.isSdkInitialized || devId.startsWith("demo_")) {
            onSuccess()
            return
        }

        try {
            val deviceInstance = ThingHomeSdk.newDeviceInstance(devId)
            val dpKey = resolveDpForChannel(devId, channelIndex)

            val dpsJson = JSONObject()
            dpsJson[dpKey] = targetState

            Log.i(TAG, "Publishing DP to $devId: $dpsJson")
            deviceInstance.publishDps(dpsJson.toJSONString(), object : com.thingclips.smart.sdk.api.IResultCallback {
                override fun onSuccess() {
                    Log.d(TAG, "Channel $channelIndex on $devId successfully set to $targetState via DP $dpKey")
                    onSuccess()
                }

                override fun onError(code: String, error: String) {
                    Log.e(TAG, "toggleChannelDp error: $code - $error (DP $dpKey)")
                    // If failed and dpKey was not standard 1-based, retry with standard key
                    val standardKey = (channelIndex + 1).toString()
                    if (dpKey != standardKey) {
                        val fallbackJson = JSONObject()
                        fallbackJson[standardKey] = targetState
                        deviceInstance.publishDps(fallbackJson.toJSONString(), object : com.thingclips.smart.sdk.api.IResultCallback {
                            override fun onSuccess() {
                                Log.d(TAG, "Fallback to DP $standardKey succeeded")
                                onSuccess()
                            }
                            override fun onError(fCode: String, fErr: String) {
                                onError(fCode, fErr)
                            }
                        })
                    } else {
                        onError(code, error)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "toggleChannelDp exception: ${e.message}")
            onError("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    /**
     * Mendaftarkan listener real-time untuk perubahan status / DP perangkat.
     */
    fun registerDeviceListener(devId: String, listener: IDevListener) {
        ThingHomeSdk.newDeviceInstance(devId).registerDevListener(listener)
    }

    fun unregisterDeviceListener(devId: String) {
        ThingHomeSdk.newDeviceInstance(devId).unRegisterDevListener()
    }
}
