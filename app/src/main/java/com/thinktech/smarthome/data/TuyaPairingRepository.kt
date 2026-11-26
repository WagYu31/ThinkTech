package com.thinktech.smarthome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.thingclips.smart.android.ble.api.BleConfigType
import com.thingclips.smart.android.ble.api.ScanDeviceBean
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.ConfigProductInfoBean
import com.thingclips.smart.home.sdk.builder.ActivatorBuilder
import com.thingclips.smart.home.sdk.builder.ThingCameraActivatorBuilder
import com.thingclips.smart.sdk.api.IBleActivator
import com.thingclips.smart.sdk.api.IBleActivatorListener
import com.thingclips.smart.sdk.api.IMultiModeActivator
import com.thingclips.smart.sdk.api.IMultiModeActivatorListener
import com.thingclips.smart.sdk.api.IThingActivator
import com.thingclips.smart.sdk.api.IThingActivatorGetToken
import com.thingclips.smart.sdk.api.IThingCameraDevActivator
import com.thingclips.smart.sdk.api.IThingDataCallback
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener
import com.thingclips.smart.sdk.api.IThingSmartCameraActivatorListener
import com.thingclips.smart.sdk.bean.BleActivatorBean
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.bean.MultiModeActivatorBean
import com.thingclips.smart.sdk.enums.ActivatorModelEnum
import com.thinktech.smarthome.ThinkTechApp
import java.util.concurrent.ConcurrentHashMap

class TuyaPairingRepository {

    companion object {
        private const val TAG = "TuyaPairingRepo"
        private val MULTI_MODE_TYPES = setOf(
            101, 301, 303, 304, 305, 306, 307,
            401, 403, 404, 405, 413, 414, 415
        )

        val scannedBeansByUuid = ConcurrentHashMap<String, ScanDeviceBean>()
        val resolvedInfoByUuid = ConcurrentHashMap<String, ConfigProductInfoBean>()
        private val pendingInfoCallbacks = ConcurrentHashMap<String, MutableList<(ConfigProductInfoBean?) -> Unit>>()

        fun cacheScannedDevice(bean: ScanDeviceBean) {
            val uuid = bean.uuid?.trim().orEmpty()
            if (uuid.isNotEmpty()) {
                scannedBeansByUuid[uuid] = bean
            }
        }

        fun resolveScannedDeviceInfo(
            bean: ScanDeviceBean,
            onResolved: (ConfigProductInfoBean?) -> Unit
        ) {
            val uuid = bean.uuid?.trim().orEmpty()
            if (uuid.isEmpty() || !ThinkTechApp.isSdkInitialized) {
                onResolved(null)
                return
            }

            resolvedInfoByUuid[uuid]?.let { cached ->
                onResolved(cached)
                return
            }

            synchronized(pendingInfoCallbacks) {
                val existingList = pendingInfoCallbacks[uuid]
                if (existingList != null) {
                    existingList.add(onResolved)
                    return
                }
                pendingInfoCallbacks[uuid] = mutableListOf(onResolved)
            }

            try {
                ThingHomeSdk.getActivatorInstance().getActivatorDeviceInfo(
                    bean.productId ?: "",
                    uuid,
                    bean.mac ?: "",
                    object : IThingDataCallback<ConfigProductInfoBean> {
                        override fun onSuccess(result: ConfigProductInfoBean?) {
                            if (result != null) {
                                resolvedInfoByUuid[uuid] = result
                            }
                            val callbacks = synchronized(pendingInfoCallbacks) {
                                pendingInfoCallbacks.remove(uuid)
                            }
                            callbacks?.forEach { it.invoke(result) }
                        }

                        override fun onError(errorCode: String?, errorMessage: String?) {
                            Log.w(TAG, "getActivatorDeviceInfo error ($uuid): $errorCode - $errorMessage")
                            val callbacks = synchronized(pendingInfoCallbacks) {
                                pendingInfoCallbacks.remove(uuid)
                            }
                            callbacks?.forEach { it.invoke(null) }
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.w(TAG, "getActivatorDeviceInfo exception: ${t.message}")
                val callbacks = synchronized(pendingInfoCallbacks) {
                    pendingInfoCallbacks.remove(uuid)
                }
                callbacks?.forEach { it.invoke(null) }
            }
        }

        fun findMatchingScannedBean(targetName: String, targetCategory: String): ScanDeviceBean? {
            if (scannedBeansByUuid.isEmpty()) return null
            val cleanTarget = targetName.replace("\n", " ").trim()
            for ((uuid, bean) in scannedBeansByUuid) {
                val resolvedName = resolvedInfoByUuid[uuid]?.name?.trim().orEmpty()
                if (resolvedName.isNotEmpty()) {
                    if (resolvedName.equals(cleanTarget, ignoreCase = true)) return bean
                    val classified = TuyaDeviceClassifier.classify(rawName = resolvedName, productId = bean.productId)
                    if (classified.displayName.equals(cleanTarget, ignoreCase = true) ||
                        classified.category.equals(targetCategory, ignoreCase = true)
                    ) {
                        return bean
                    }
                }
            }
            return if (scannedBeansByUuid.size == 1) scannedBeansByUuid.values.firstOrNull() else null
        }
    }

    private var currentActivator: IThingActivator? = null
    private var currentBleActivator: IBleActivator? = null
    private var currentMultiModeActivator: IMultiModeActivator? = null
    private var currentCameraActivator: IThingCameraDevActivator? = null
    private var currentActiveUuid: String? = null

    /**
     * Memulai proses pairing Dual-Mode (BLE + Wi-Fi) atau Single-BLE untuk perangkat Tuya asli.
     */
    fun startMultiModePairing(
        context: Context,
        homeId: Long,
        scanDeviceBean: ScanDeviceBean?,
        ssid: String,
        pass: String,
        timeoutSec: Long = 120,
        onDeviceFound: (DeviceBean) -> Unit,
        onError: (code: String, error: String) -> Unit,
        onStepUpdate: (step: String) -> Unit
    ) {
        onStepUpdate("Menghubungkan ke Tuya Cloud...")

        if (!ThinkTechApp.isSdkInitialized) {
            onError("SDK_NOT_INITIALIZED", "Tuya SDK belum siap")
            return
        }

        // Stop active BLE scan before opening GATT channel to prevent Android BLE contention (status 133)
        try {
            ThingHomeSdk.getBleOperator().stopLeScan()
        } catch (_: Throwable) {}

        TuyaDeviceRepository.ensureHomeId(
            onReady = { validHomeId ->
                val validScanBean = scanDeviceBean?.takeIf { !it.uuid.isNullOrBlank() }
                if (validScanBean != null) {
                    val uuid = validScanBean.uuid.trim()
                    currentActiveUuid = uuid

                    // Pure Single-BLE device (no Wi-Fi credentials required)
                    if (validScanBean.configType == BleConfigType.CONFIG_TYPE_SINGLE.type) {
                        onStepUpdate("Menghubungkan perangkat Bluetooth ke Tuya Cloud...")
                        try {
                            currentBleActivator?.stopActivator(uuid)
                        } catch (_: Throwable) {}

                        val bleBean = BleActivatorBean(validScanBean).apply {
                            this.homeId = validHomeId
                            this.uuid = uuid
                            this.address = validScanBean.address?.takeIf { it.isNotBlank() } ?: validScanBean.mac ?: ""
                            this.productId = validScanBean.productId
                            this.deviceType = validScanBean.deviceType
                            this.timeout = maxOf(timeoutSec * 1000L, 60_000L)
                            this.retryCount = 3
                        }

                        try {
                            currentBleActivator = ThingHomeSdk.getActivator().newBleActivator()
                            currentBleActivator?.startActivator(
                                bleBean,
                                object : IBleActivatorListener {
                                    override fun onSuccess(devResp: DeviceBean?) {
                                        if (devResp != null) {
                                            scannedBeansByUuid.remove(uuid)
                                            onDeviceFound(devResp)
                                        } else {
                                            onError("EMPTY_DEVICE", "Respons perangkat kosong dari Tuya Cloud")
                                        }
                                    }

                                    override fun onFailure(code: Int, msg: String?, handle: Any?) {
                                        Log.e(TAG, "Single BLE pairing error ($uuid): $code - $msg")
                                        onError(code.toString(), msg ?: "Gagal menghubungkan perangkat Bluetooth")
                                    }
                                }
                            )
                        } catch (t: Throwable) {
                            Log.e(TAG, "Single BLE init error: ${t.message}", t)
                            onError("BLE_INIT_ERR", t.message ?: "Gagal memulai aktivasi Bluetooth")
                        }
                        return@ensureHomeId
                    }

                    // BLE + Wi-Fi Combo / Dual-Mode device (WiFi 3ch switch, BLE+Wi-Fi Security Camera, etc.)
                    onStepUpdate("Mengambil pairing token dari Tuya Cloud...")
                    ThingHomeSdk.getActivatorInstance().getActivatorToken(
                        validHomeId,
                        object : IThingActivatorGetToken {
                            override fun onSuccess(token: String?) {
                                if (token.isNullOrEmpty()) {
                                    onError("TOKEN_EMPTY", "Gagal mendapatkan token pairing.")
                                    return
                                }

                                onStepUpdate("Mengirim kredensial Wi-Fi $ssid ke perangkat...")

                                val resolvedDeviceType = if (validScanBean.deviceType in MULTI_MODE_TYPES) {
                                    validScanBean.deviceType
                                } else {
                                    301
                                }
                                val resolvedAddress = validScanBean.address?.takeIf { it.isNotBlank() }
                                    ?: validScanBean.mac?.takeIf { it.isNotBlank() }
                                    ?: ""

                                val activatorBean = MultiModeActivatorBean(validScanBean).apply {
                                    this.homeId = validHomeId
                                    this.uuid = uuid
                                    this.address = resolvedAddress
                                    this.mac = validScanBean.mac
                                    this.deviceType = resolvedDeviceType
                                    this.flag = validScanBean.flag
                                    this.productId = validScanBean.productId
                                    this.ssid = ssid
                                    this.pwd = pass
                                    this.token = token
                                    this.timeout = maxOf(timeoutSec * 1000L, 120_000L)
                                    this.phase1Timeout = 60_000L
                                    this.retryCount = 3
                                    this.bleActive = resolvedInfoByUuid[uuid]?.supportPlugPlay ?: true
                                }

                                startMultiModeWithFallback(
                                    uuid = uuid,
                                    activatorBean = activatorBean,
                                    allowBleWifiRetry = activatorBean.bleActive && resolvedDeviceType in setOf(304, 305, 404, 405),
                                    onDeviceFound = onDeviceFound,
                                    onError = onError,
                                    onStepUpdate = onStepUpdate
                                )
                            }

                            override fun onFailure(errorCode: String, errorMsg: String) {
                                Log.e(TAG, "getActivatorToken onFailure: $errorCode - $errorMsg")
                                onError(errorCode, errorMsg)
                            }
                        }
                    )
                } else {
                    startEzPairingInternal(context, validHomeId, ssid, pass, timeoutSec, onDeviceFound, onError, onStepUpdate)
                }
            },
            onError = { code, error ->
                onError(code, error)
            }
        )
    }

    private fun startMultiModeWithFallback(
        uuid: String,
        activatorBean: MultiModeActivatorBean,
        allowBleWifiRetry: Boolean,
        onDeviceFound: (DeviceBean) -> Unit,
        onError: (code: String, error: String) -> Unit,
        onStepUpdate: (step: String) -> Unit
    ) {
        try {
            val multiActivator = ThingHomeSdk.getActivator().newMultiModeActivator()
            currentMultiModeActivator = multiActivator
            try {
                multiActivator.stopActivator(uuid)
            } catch (_: Throwable) {}

            Log.i(
                TAG,
                "Starting MultiModeActivator uuid=$uuid, address=${activatorBean.address}, " +
                        "type=${activatorBean.deviceType}, pid=${activatorBean.productId}, bleActive=${activatorBean.bleActive}"
            )

            multiActivator.startActivator(
                activatorBean,
                object : IMultiModeActivatorListener {
                    override fun onSuccess(devResp: DeviceBean?) {
                        if (devResp != null) {
                            Log.i(TAG, "MultiMode pairing success: ${devResp.name} (${devResp.devId})")
                            scannedBeansByUuid.remove(uuid)
                            onDeviceFound(devResp)
                        } else {
                            onError("EMPTY_DEVICE", "Respons perangkat kosong dari Tuya Cloud")
                        }
                    }

                    override fun onFailure(code: Int, errorMsg: String?, obj: Any?) {
                        Log.e(TAG, "MultiMode pairing error ($uuid, bleActive=${activatorBean.bleActive}): $code - $errorMsg")
                        if (allowBleWifiRetry && currentActiveUuid == uuid) {
                            onStepUpdate("Menghubungkan ulang Wi-Fi ${activatorBean.ssid} ke perangkat...")
                            activatorBean.bleActive = false
                            startMultiModeWithFallback(
                                uuid = uuid,
                                activatorBean = activatorBean,
                                allowBleWifiRetry = false,
                                onDeviceFound = onDeviceFound,
                                onError = onError,
                                onStepUpdate = onStepUpdate
                            )
                        } else {
                            onError(code.toString(), errorMsg ?: "Gagal menghubungkan perangkat via Bluetooth & Wi-Fi")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "MultiMode init error: ${e.message}", e)
            onError("MULTIMODE_INIT_ERR", e.message ?: "Gagal memulai aktivasi Bluetooth & Wi-Fi")
        }
    }

    /**
     * Memulai proses pairing Wi-Fi EZ Mode untuk mencari dan menghubungkan perangkat baru ke Tuya Cloud.
     */
    fun startEzPairing(
        context: Context,
        homeId: Long,
        ssid: String,
        pass: String,
        timeoutSec: Long = 100,
        onDeviceFound: (DeviceBean) -> Unit,
        onError: (code: String, error: String) -> Unit,
        onStepUpdate: (step: String) -> Unit
    ) {
        if (!ThinkTechApp.isSdkInitialized) {
            onError("SDK_NOT_INITIALIZED", "Tuya SDK belum siap")
            return
        }

        TuyaDeviceRepository.ensureHomeId(
            onReady = { validHomeId ->
                startEzPairingInternal(context, validHomeId, ssid, pass, timeoutSec, onDeviceFound, onError, onStepUpdate)
            },
            onError = { code, error ->
                onError(code, error)
            }
        )
    }

    private fun startEzPairingInternal(
        context: Context,
        validHomeId: Long,
        ssid: String,
        pass: String,
        timeoutSec: Long = 100,
        onDeviceFound: (DeviceBean) -> Unit,
        onError: (code: String, error: String) -> Unit,
        onStepUpdate: (step: String) -> Unit
    ) {
        onStepUpdate("Mengambil pairing token dari Tuya Cloud...")

        ThingHomeSdk.getActivatorInstance().getActivatorToken(
            validHomeId,
            object : IThingActivatorGetToken {
                override fun onSuccess(token: String?) {
                    if (token.isNullOrEmpty()) {
                        onError("TOKEN_EMPTY", "Gagal mendapatkan token pairing.")
                        return
                    }

                    onStepUpdate("Mencari sinyal perangkat pada Wi-Fi $ssid...")

                    val builder = ActivatorBuilder()
                        .setContext(context)
                        .setSsid(ssid)
                        .setPassword(pass)
                        .setToken(token)
                        .setTimeOut(timeoutSec)
                        .setActivatorModel(ActivatorModelEnum.THING_EZ)
                        .setListener(object : IThingSmartActivatorListener {
                            override fun onActiveSuccess(devResp: DeviceBean) {
                                Log.i(TAG, "Device EZ pairing success: ${devResp.name} (${devResp.devId})")
                                onDeviceFound(devResp)
                            }

                            override fun onError(errorCode: String, errorMsg: String) {
                                Log.e(TAG, "EZ pairing error: $errorCode - $errorMsg")
                                onError(errorCode, errorMsg)
                            }

                            override fun onStep(step: String, data: Any?) {
                                Log.d(TAG, "Pairing step: $step")
                                onStepUpdate(step)
                            }
                        })

                    try {
                        currentActivator = ThingHomeSdk.getActivatorInstance().newActivator(builder)
                        currentActivator?.start()
                    } catch (t: Throwable) {
                        Log.e(TAG, "EZ Activator error: ${t.message}")
                        onError("ACTIVATOR_ERROR", t.message ?: "Gagal memulai pairing Wi-Fi")
                    }
                }

                override fun onFailure(errorCode: String, errorMsg: String) {
                    Log.e(TAG, "getActivatorToken onFailure: $errorCode - $errorMsg")
                    onError(errorCode, errorMsg)
                }
            }
        )
    }

    /**
     * Memulai proses pairing Kamera CCTV Tuya via QR Code.
     * Kamera membaca QR code di layar ponsel untuk mendapatkan SSID dan Password Wi-Fi.
     */
    fun startCameraQrPairing(
        context: Context,
        validHomeId: Long,
        ssid: String,
        pass: String,
        timeoutSec: Long = 100,
        onQrCodeGenerated: (Bitmap) -> Unit,
        onDeviceFound: (DeviceBean) -> Unit,
        onError: (code: String, error: String) -> Unit,
        onStepUpdate: (step: String) -> Unit
    ) {
        onStepUpdate("Mengambil pairing token kamera dari Tuya Cloud...")

        ThingHomeSdk.getActivatorInstance().getActivatorToken(
            validHomeId,
            object : IThingActivatorGetToken {
                override fun onSuccess(token: String?) {
                    if (token.isNullOrEmpty()) {
                        onError("TOKEN_EMPTY", "Gagal mendapatkan token pairing.")
                        return
                    }

                    onStepUpdate("Menyiapkan Kode QR...")

                    val builder = ThingCameraActivatorBuilder()
                        .setContext(context)
                        .setSsid(ssid)
                        .setPassword(pass)
                        .setToken(token)
                        .setTimeOut(timeoutSec)
                        .setListener(object : IThingSmartCameraActivatorListener {
                            override fun onQRCodeSuccess(qrcodeUrl: String?) {
                                Log.i(TAG, "Camera QR Code content generated: $qrcodeUrl")
                                val qrContent = if (!qrcodeUrl.isNullOrBlank()) {
                                    qrcodeUrl
                                } else {
                                    "{\"s\":\"$ssid\",\"p\":\"$pass\",\"t\":\"$token\"}"
                                }
                                try {
                                    val bitmap = generateQrBitmap(qrContent, 600)
                                    onQrCodeGenerated(bitmap)
                                    onStepUpdate("Arahkan Kode QR ke lensa kamera (jarak 15-20 cm)...")
                                } catch (e: Exception) {
                                    Log.e(TAG, "QR generate error: ${e.message}")
                                    onError("QR_GEN_FAIL", "Gagal membuat gambar QR: ${e.message}")
                                }
                            }

                            override fun onError(errorCode: String?, errorMsg: String?) {
                                Log.e(TAG, "Camera QR pairing error: $errorCode - $errorMsg")
                                onError(errorCode ?: "ERR", errorMsg ?: "Pairing kamera gagal")
                            }

                            override fun onActiveSuccess(devResp: DeviceBean?) {
                                if (devResp != null) {
                                    Log.i(TAG, "Camera pairing success: ${devResp.name} (${devResp.devId})")
                                    onDeviceFound(devResp)
                                }
                            }
                        })

                    try {
                        currentCameraActivator?.stop()
                        currentCameraActivator = ThingHomeSdk.getActivatorInstance().newCameraDevActivator(builder)
                        currentCameraActivator?.createQRCode()
                        currentCameraActivator?.start()
                    } catch (t: Throwable) {
                        Log.e(TAG, "newCameraDevActivator error: ${t.message}")
                        // Fallback manual standard QR code payload for Tuya cameras
                        val qrContent = "{\"s\":\"$ssid\",\"p\":\"$pass\",\"t\":\"$token\"}"
                        try {
                            val bitmap = generateQrBitmap(qrContent, 600)
                            onQrCodeGenerated(bitmap)
                            onStepUpdate("Arahkan Kode QR ke lensa kamera (jarak 15-20 cm)...")
                        } catch (e: Exception) {
                            onError("QR_FAIL", "Gagal membuat QR code: ${e.message}")
                        }
                    }
                }

                override fun onFailure(errorCode: String, errorMsg: String) {
                    onError(errorCode, errorMsg)
                }
            }
        )
    }

    fun generateQrBitmap(content: String, size: Int = 600): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun stopPairing() {
        try {
            val activeUuid = currentActiveUuid
            currentActiveUuid = null
            currentActivator?.stop()
            currentActivator?.onDestroy()
            currentActivator = null
            if (!activeUuid.isNullOrBlank()) {
                try { currentBleActivator?.stopActivator(activeUuid) } catch (_: Throwable) {}
                try { currentMultiModeActivator?.stopActivator(activeUuid) } catch (_: Throwable) {}
            }
            currentBleActivator = null
            currentMultiModeActivator = null
            currentCameraActivator?.stop()
            currentCameraActivator?.onDestroy()
            currentCameraActivator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping pairing: ${e.message}")
        }
    }
}
