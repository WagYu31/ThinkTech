package com.thinktech.smarthome.ui.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk
import com.thingclips.smart.android.camera.sdk.api.IThingIPCDpHelper
import com.thingclips.smart.android.camera.sdk.constant.PTZDPModel
import com.thingclips.smart.android.camera.sdk.constant.ThingIPCConstant
import com.thingclips.smart.camera.camerasdk.bean.ThingVideoFrameInfo
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.IRegistorIOTCListener
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P
import com.thingclips.smart.camera.ipccamerasdk.utils.CameraConstant
import com.thingclips.smart.camera.middleware.p2p.IThingSmartCameraP2P
import com.thingclips.smart.camera.middleware.p2p.ThingSmartCameraP2P
import com.thingclips.smart.camera.middleware.p2p.ThingSmartCameraP2PSync
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.api.IThingHome
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thinktech.smarthome.R
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.databinding.ActivityCameraControlBinding
import com.thinktech.smarthome.ui.home.MainActivity
import com.thinktech.smarthome.ui.pairing.PairDeviceActivity
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class CameraControlActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraControlActivity"
        const val EXTRA_VOICE_CAMERA_ACTION = "VOICE_CAMERA_ACTION"
        private const val APP_DIAG_VER = "v1.0.9"
        private const val TUTK_CAMERA_CLASS = "com.thingclips.smart.camera.camerasdk.IPCTutkP2PCamera"
        private const val EAPIL_CAMERA_CLASS = "com.thingclips.smart.ipc.fisheye.sdk.EapilCameraPlayer"
        private const val HOME_SYNC_TIMEOUT_MS = 3_500L
        private const val STREAM_WATCHDOG_MS = 30_000L
        private const val RECONNECT_DISCONNECT_GUARD_MS = 2_000L

        // shortcut: maps p2pType=1 (TUTK) to V2 when optional TUTK AAR is absent, add thingsmart-tutk if legacy pre-2019 TUTK camera is used
        internal fun sanitizeSkillsP2pType(
            existingSkills: Map<String, Any?>?,
            hasTutkSdk: Boolean
        ): HashMap<String, Any> {
            val clean = HashMap<String, Any>()
            existingSkills?.forEach { (k, v) -> if (v != null) clean[k] = v }
            val parsed = when (val raw = clean["p2pType"]) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }
            val safeType = when {
                parsed == null || parsed <= 0 -> CameraConstant.SDK_PROVIDER_V2
                parsed == 1 && !hasTutkSdk -> CameraConstant.SDK_PROVIDER_V2
                else -> parsed
            }
            clean["p2pType"] = Integer.valueOf(safeType)
            return clean
        }

        internal fun resolveInitialClarity(configDefaultDefinition: Int?): Int =
            when (configDefaultDefinition) {
                ThingIPCConstant.DEFINITION_SD -> ThingIPCConstant.DEFINITION_SD
                ThingIPCConstant.DEFINITION_HD -> ThingIPCConstant.DEFINITION_HD
                else -> ThingIPCConstant.DEFINITION_HD
            }

        internal fun fallbackClarity(clarity: Int): Int =
            if (clarity == ThingIPCConstant.DEFINITION_HD) {
                ThingIPCConstant.DEFINITION_SD
            } else {
                ThingIPCConstant.DEFINITION_HD
            }

        internal fun resolveStrictPrivacyMode(
            dps: Map<String, Any?>?,
            schemaCodeToId: Map<String, String>?
        ): Boolean? {
            if (dps == null || schemaCodeToId.isNullOrEmpty()) return null
            val privacyDpId = schemaCodeToId.entries
                .firstOrNull { it.key.equals("basic_private", ignoreCase = true) }
                ?.value
                ?: return null
            val raw = dps[privacyDpId] ?: return null
            return when (raw) {
                is Boolean -> raw
                else -> raw.toString().trim().toBooleanStrictOrNull()
            }
        }

        private fun hasRuntimeClass(className: String): Boolean =
            runCatching { Class.forName(className) }.isSuccess
    }

    private lateinit var binding: ActivityCameraControlBinding
    private val deviceRepo = TuyaDeviceRepository()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var devId: String = ""
    private var devName: String = "Security Camera"
    private var productId: String = ""
    private var isOnline: Boolean = true
    private var isFromPairing: Boolean = false
    private var hasVerifiedCloudMembership: Boolean = false
    private var isCameraUnboundFromCloud: Boolean = false
    private var activeP2pProvider: Int = CameraConstant.SDK_PROVIDER_V2

    private var cameraP2P: IThingSmartCameraP2P<Any>? = null
    private var dpHelper: IThingIPCDpHelper? = null
    private var activeHomeSyncInstance: IThingHome? = null
    private var boundMonitorView: Any? = null
    @Volatile private var syncedMonitorType: Int = -1
    @Volatile private var syncedMonitorIndex: Int = -1

    private var isVideoViewCreated = false
    private var isVideoSurfaceReady = false
    private var isInitialClarityResolved = false
    private var isFetchingHomeDetail = false
    private var isReconnectingSession = false
    @Volatile private var isConnectingP2p = false
    @Volatile private var isP2pConnected = false
    @Volatile private var isPreviewing = false
    private var isMuted = true
    private var isTalking = false
    private var isRecording = false
    private var isSirenActive = false
    private var isPrivateModeOn = false
    private var isSuppressingSwitchCallbacks = false
    private var currentClarity = ThingIPCConstant.DEFINITION_HD
    private var currentNightVision = "0" // "0" Auto, "1" Off, "2" On

    private val homeSyncTimeoutRunnable = Runnable {
        if (isFinishing || isDestroyed || !isFetchingHomeDetail) return@Runnable
        Log.w(TAG, "getHomeDetail timed out after ${HOME_SYNC_TIMEOUT_MS}ms (ThingExecutor discard guard) for $devId")
        isFetchingHomeDetail = false
        val cachedBean = runCatching { ThingHomeSdk.getDataInstance().getDeviceBean(devId) }.getOrNull()
        if (cachedBean != null && ensureCameraSessionInitialized(cachedBean)) {
            connectAndPlay()
        } else {
            showStreamOverlay(
                "Sinkronisasi data rumah Tuya melebihi batas waktu — ketuk Hubungkan Ulang.",
                showSpinner = false,
                showRetry = true,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Home sync timeout (${HOME_SYNC_TIMEOUT_MS / 1000}d)"
            )
        }
    }

    private val connectTimeoutRunnable = Runnable {
        if (isFinishing || isDestroyed || !isConnectingP2p) return@Runnable
        Log.w(TAG, "P2P connect watchdog timeout after ${STREAM_WATCHDOG_MS}ms for $devId")
        isConnectingP2p = false
        isP2pConnected = false
        isPreviewing = false
        detachStuckCameraP2p()
        if (!isPrivateModeOn) {
            showStreamOverlay(
                "Koneksi video P2P timeout — pastikan sinyal Wi-Fi kamera stabil lalu coba lagi.",
                showSpinner = false,
                showRetry = true,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Timeout ${STREAM_WATCHDOG_MS / 1000}d"
            )
        }
    }

    private val previewTimeoutRunnable = Runnable {
        if (isFinishing || isDestroyed || isPreviewing) return@Runnable
        Log.w(TAG, "P2P startPreview watchdog timeout after ${STREAM_WATCHDOG_MS}ms for $devId")
        isConnectingP2p = false
        isP2pConnected = false
        isPreviewing = false
        detachStuckCameraP2p()
        if (!isPrivateModeOn) {
            showStreamOverlay(
                "Tayangan video timeout — ketuk Hubungkan Ulang untuk menyegarkan stream.",
                showSpinner = false,
                showRetry = true,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Stream tidak merespons"
            )
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleTwoWayTalk()
        } else {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk fitur bicara 2 arah.", Toast.LENGTH_SHORT).show()
        }
    }

    private val p2pListener = object : AbsP2pCameraListener() {
        override fun onReceiveFrameYUVData(
            sessionId: Int,
            y: ByteBuffer?,
            u: ByteBuffer?,
            v: ByteBuffer?,
            videoFrameInfo: ThingVideoFrameInfo?,
            camera: Any?
        ) {
            if (videoFrameInfo != null) {
                val frameType = videoFrameInfo.type
                val frameIndex = videoFrameInfo.index
                if (syncedMonitorType != frameType || syncedMonitorIndex != frameIndex) {
                    syncedMonitorType = frameType
                    syncedMonitorIndex = frameIndex
                    runCatching {
                        binding.cameraVideoView.setType(frameType)
                        binding.cameraVideoView.setIndex(frameIndex)
                        (boundMonitorView as? IRegistorIOTCListener)?.receiveFrameYUVData(
                            sessionId,
                            y,
                            u,
                            v,
                            videoFrameInfo,
                            camera
                        )
                    }
                }
            }
            super.onReceiveFrameYUVData(sessionId, y, u, v, videoFrameInfo, camera)
        }

        override fun onReceiveFrameYUVData(
            sessionId: Int,
            y: ByteBuffer?,
            u: ByteBuffer?,
            v: ByteBuffer?,
            width: Int,
            height: Int,
            frameRate: Int,
            isKeyFrame: Int,
            timestamp: Long,
            progress: Long,
            duration: Long,
            camera: Any?
        ) {
            super.onReceiveFrameYUVData(sessionId, y, u, v, width, height, frameRate, isKeyFrame, timestamp, progress, duration, camera)
            if (!isPreviewing && !isPrivateModeOn && width > 0 && height > 0) {
                isPreviewing = true
                isConnectingP2p = false
                isP2pConnected = true
                runOnUiThread {
                    if (isFinishing || isDestroyed || isPrivateModeOn) return@runOnUiThread
                    cancelStreamWatchdogs()
                    binding.layoutStreamOverlay.visibility = View.GONE
                    updateClarityUi()
                }
            }
        }

        override fun onSessionStatusChanged(camera: Any?, sessionId: Int, sessionStatus: Int) {
            super.onSessionStatusChanged(camera, sessionId, sessionStatus)
            if (sessionStatus < 0) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    cancelStreamWatchdogs()
                    isConnectingP2p = false
                    isP2pConnected = false
                    isPreviewing = false
                    if (!isPrivateModeOn) {
                        showStreamOverlay(
                            "Sesi video terputus ($sessionStatus)",
                            showSpinner = false,
                            showRetry = true,
                            diagText = "ID: ${devId.take(8)} • P2P V$activeP2pProvider • Status sesi: $sessionStatus"
                        )
                    }
                }
            }
        }
    }

    private val devListener = object : IDevListener {
        override fun onDpUpdate(devId: String?, dpStr: String?) {
            runOnUiThread {
                if (isFinishing || isDestroyed || dpStr.isNullOrBlank()) return@runOnUiThread
                syncUiFromRawDps(JSON.parseObject(dpStr))
            }
        }

        override fun onRemoved(devId: String?) {
            runOnUiThread {
                Toast.makeText(this@CameraControlActivity, "Kamera telah dihapus.", Toast.LENGTH_SHORT).show()
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

    private var pendingVoiceCameraAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devId = intent.getStringExtra("DEV_ID").orEmpty()
        val rawName = intent.getStringExtra("DEV_NAME")
        devName = if (rawName.isNullOrBlank() || rawName.equals("null", true)) "Security Camera" else rawName
        productId = intent.getStringExtra("PRODUCT_ID").orEmpty()
        isOnline = intent.getBooleanExtra("IS_ONLINE", true)
        isFromPairing = intent.getBooleanExtra("IS_FROM_PAIRING", false)
        pendingVoiceCameraAction = intent.getStringExtra(EXTRA_VOICE_CAMERA_ACTION)

        setupHarmonyCameraChrome()
        setupHeaderUi()
        setupControls()
        syncInitialCameraDps()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    navigateBack()
                }
            }
        })
    }

    private fun setupHarmonyCameraChrome() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        applyVideoContainerRadius(isFullscreen = isLandscape)
        syncSystemBars(isLandscape = isLandscape)

        binding.btnBack.elevation = 1.5f * density
        binding.btnCameraOptions.elevation = 1.5f * density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val softSpot = 0x1A0A192F
            val softAmbient = 0x0C0A192F
            val microSpot = 0x120A192F
            binding.flVideoContainer.outlineSpotShadowColor = softSpot
            binding.flVideoContainer.outlineAmbientShadowColor = softAmbient
            binding.btnPtzStop.outlineSpotShadowColor = softSpot
            binding.btnPtzStop.outlineAmbientShadowColor = softAmbient
            binding.btnBack.outlineSpotShadowColor = microSpot
            binding.btnBack.outlineAmbientShadowColor = softAmbient
            binding.btnCameraOptions.outlineSpotShadowColor = microSpot
            binding.btnCameraOptions.outlineAmbientShadowColor = softAmbient
        }
    }

    private fun applyVideoContainerRadius(isFullscreen: Boolean) {
        val radiusPx = if (isFullscreen) 0f else 22f * resources.displayMetrics.density
        binding.flVideoContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        binding.flVideoContainer.clipToOutline = true
        binding.flVideoContainer.invalidateOutline()
    }

    private fun syncSystemBars(isLandscape: Boolean) {
        val barColor = if (isLandscape) Color.parseColor("#0B0E14") else Color.parseColor("#F1F3F5")
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isLandscape
        controller.isAppearanceLightNavigationBars = !isLandscape
    }

    private fun setupHeaderUi() {
        binding.tvCameraTitle.text = devName
        updateHeaderStatus()
        updateClarityUi()
        updateAudioMuteUi()
        updateTwoWayTalkUi()
        updateRecordUi()
    }

    private fun updateHeaderStatus() {
        val isDemo = devId.isBlank() || devId.startsWith("demo_")
        when {
            isDemo -> {
                binding.tvCameraSubtitle.text = "Mode Demo"
                binding.tvCameraSubtitle.setTextColor(Color.parseColor("#D97706"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            }
            isCameraUnboundFromCloud -> {
                binding.tvCameraSubtitle.text = "Perlu Pair Ulang"
                binding.tvCameraSubtitle.setTextColor(Color.parseColor("#E84026"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E84026"))
            }
            isOnline -> {
                binding.tvCameraSubtitle.text = "Online • Live"
                binding.tvCameraSubtitle.setTextColor(Color.parseColor("#00996B"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00B37E"))
            }
            else -> {
                binding.tvCameraSubtitle.text = "Offline"
                binding.tvCameraSubtitle.setTextColor(Color.parseColor("#9398A2"))
                binding.dotConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A7ACB5"))
            }
        }
    }

    private fun cancelStreamWatchdogs() {
        mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        mainHandler.removeCallbacks(previewTimeoutRunnable)
    }

    private fun updateClarityUi() {
        binding.btnToggleClarity.text = if (currentClarity == ThingIPCConstant.DEFINITION_HD) "HD" else "SD"
        binding.tvLiveBadge.text = when {
            !isPreviewing -> "P2P"
            currentClarity == ThingIPCConstant.DEFINITION_HD -> "LIVE • HD"
            else -> "LIVE • SD"
        }
        binding.dotLiveIndicator.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (isPreviewing) "#FF3B30" else "#F59E0B")
        )
    }

    private fun hasValidNativeP2P(p2p: IThingSmartCameraP2P<Any>?): Boolean {
        if (p2p == null) return false
        return runCatching {
            val field = ThingSmartCameraP2PSync::class.java.getDeclaredField("mCameraP2P")
            field.isAccessible = true
            field.get(p2p) != null
        }.getOrDefault(true)
    }

    private fun detachStuckCameraP2p() {
        val oldP2p = cameraP2P ?: return
        cameraP2P = null
        runCatching {
            oldP2p.cancelBusiness()
            oldP2p.removeOnP2PCameraListener(p2pListener)
            oldP2p.disconnect(null)
        }
    }

    private fun destroyCameraP2pFinal() {
        cancelStreamWatchdogs()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            cameraP2P?.cancelBusiness()
            cameraP2P?.removeOnP2PCameraListener(p2pListener)
            cameraP2P?.disconnect(null)
            cameraP2P?.destroyP2P()
        } catch (_: Throwable) {}
        cameraP2P = null
        isReconnectingSession = false
        isConnectingP2p = false
        isP2pConnected = false
        isPreviewing = false
    }

    // shortcut: detaches blocked executor immediately when connect is stuck and skips disconnect when idle, switch to ViewModel-scoped session pool if multi-camera grid is added
    private fun disconnectAndReconnect() {
        val p2p = cameraP2P
        val wasConnectedOrPreviewing = isP2pConnected || isPreviewing
        val wasConnectingInFlight = isConnectingP2p
        cancelStreamWatchdogs()
        isConnectingP2p = false
        isP2pConnected = false
        isPreviewing = false
        updateClarityUi()

        if (p2p == null || !wasConnectedOrPreviewing) {
            if (wasConnectingInFlight) {
                detachStuckCameraP2p()
            }
            isReconnectingSession = false
            ensureDeviceAndConnect(forceReconnect = false)
            return
        }
        if (isReconnectingSession) return
        isReconnectingSession = true
        showStreamOverlay(
            "Menyegarkan sesi video P2P...",
            showSpinner = true,
            showRetry = false,
            diagText = "ID: ${devId.take(8)} • P2P V$activeP2pProvider • Memutus sesi lama"
        )

        val completed = AtomicBoolean(false)
        val proceed = Runnable {
            if (!completed.compareAndSet(false, true)) return@Runnable
            isReconnectingSession = false
            if (isFinishing || isDestroyed || isPrivateModeOn) return@Runnable
            ensureDeviceAndConnect(forceReconnect = false)
        }
        mainHandler.postDelayed(proceed, RECONNECT_DISCONNECT_GUARD_MS)
        try {
            p2p.disconnect(object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    mainHandler.removeCallbacks(proceed)
                    runOnUiThread(proceed)
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    mainHandler.removeCallbacks(proceed)
                    runOnUiThread(proceed)
                }
            })
        } catch (_: Throwable) {
            mainHandler.removeCallbacks(proceed)
            runOnUiThread(proceed)
        }
    }

    private fun initVideoSurfaceOnce(rawProvider: Int, resolvedProvider: Int) {
        if (isVideoViewCreated) {
            runCatching { binding.cameraVideoView.onResume() }
            val existingView = boundMonitorView ?: runCatching { binding.cameraVideoView.createdView() }.getOrNull()
            if (existingView != null) {
                boundMonitorView = existingView
                isVideoSurfaceReady = true
                cameraP2P?.generateCameraView(existingView)
            }
            return
        }
        isVideoViewCreated = true
        binding.cameraVideoView.setViewCallback(object : AbsVideoViewCallback() {
            override fun onCreated(view: Any?) {
                super.onCreated(view)
                if (view != null) {
                    boundMonitorView = view
                    syncedMonitorType = -1
                    syncedMonitorIndex = -1
                    isVideoSurfaceReady = true
                    runCatching { binding.cameraVideoView.onResume() }
                    cameraP2P?.generateCameraView(view)
                    if (isP2pConnected && !isPreviewing && !isPrivateModeOn) {
                        startLivePreview()
                    }
                }
            }
        })
        if (rawProvider == resolvedProvider) {
            runCatching { binding.cameraVideoView.createVideoView(devId) }.onFailure {
                Log.w(TAG, "createVideoView(devId) fallback to provider=$resolvedProvider: ${it.message}")
            }
        }
        if (!isVideoSurfaceReady) {
            runCatching { binding.cameraVideoView.createVideoView(resolvedProvider) }.onFailure {
                Log.e(TAG, "createVideoView(provider=$resolvedProvider) error: ${it.message}", it)
            }
        }
        runCatching { binding.cameraVideoView.createdView() }.getOrNull()?.let { view ->
            boundMonitorView = view
            isVideoSurfaceReady = true
            cameraP2P?.generateCameraView(view)
        }
        runCatching { binding.cameraVideoView.onResume() }
    }

    private fun ensureCameraSessionInitialized(devBean: DeviceBean): Boolean {
        try {
            ThinkTechApp.ensureP2pUserSessionInitialized()

            val hasTutk = hasRuntimeClass(TUTK_CAMERA_CLASS)
            @Suppress("UNCHECKED_CAST")
            devBean.skills = sanitizeSkillsP2pType(devBean.skills as? Map<String, Any?>, hasTutk)

            val rawProvider = CameraConstant.getSdkProvider(devBean)
            val resolvedProvider = when {
                rawProvider == 3 || rawProvider == 9 -> rawProvider
                rawProvider == 4 && hasRuntimeClass(EAPIL_CAMERA_CLASS) -> 4
                rawProvider == 1 && hasTutk -> 1
                else -> CameraConstant.SDK_PROVIDER_V2
            }
            activeP2pProvider = resolvedProvider

            if (dpHelper == null) {
                runCatching {
                    dpHelper = ThingIPCSdk.createIPCDpHelper(devId)?.also {
                        it.addWeakRefDevListener(devListener)
                    }
                }.onFailure { Log.w(TAG, "createIPCDpHelper warning: ${it.message}") }
            }

            syncPrivacyRowSupport(devBean)
            syncInitialCameraDps()

            val cameraCfg = runCatching {
                ThingIPCSdk.getCameraInstance()?.getCameraConfig(devId)
            }.getOrNull()

            if (!isInitialClarityResolved) {
                currentClarity = resolveInitialClarity(ThingIPCConstant.DEFINITION_HD)
                isInitialClarityResolved = true
                updateClarityUi()
            }

            // Urutan resmi Tuya CameraPanelActivity: buat cameraP2P lebih dulu sebelum createVideoView
            if (!hasValidNativeP2P(cameraP2P)) {
                runCatching { cameraP2P?.removeOnP2PCameraListener(p2pListener) }
                cameraP2P = null
                val ipcCore = ThingIPCSdk.getCameraInstance()
                @Suppress("UNCHECKED_CAST")
                var candidate = ipcCore?.createCameraP2P(devId) as? IThingSmartCameraP2P<Any>
                if (!hasValidNativeP2P(candidate)) {
                    Log.w(TAG, "Fallback to direct ThingSmartCameraP2P(provider=$resolvedProvider) for $devId")
                    @Suppress("UNCHECKED_CAST")
                    candidate = ThingSmartCameraP2P(resolvedProvider, devId) as IThingSmartCameraP2P<Any>
                }
                cameraP2P = candidate
                cameraP2P?.registerP2PCameraListener(p2pListener)
            }

            initVideoSurfaceOnce(rawProvider, resolvedProvider)
            boundMonitorView?.let { cameraP2P?.generateCameraView(it) }

            if (cameraCfg?.isSupportVideoSegmentation == true) {
                val primarySplit = cameraCfg.cameraVideoSegmentationModel?.split_info?.firstOrNull()
                if (primarySplit != null) {
                    syncedMonitorType = primarySplit.type
                    syncedMonitorIndex = primarySplit.index
                    runCatching {
                        binding.cameraVideoView.setType(primarySplit.type)
                        binding.cameraVideoView.setIndex(primarySplit.index)
                    }
                }
            }
            return hasValidNativeP2P(cameraP2P)
        } catch (t: Throwable) {
            Log.e(TAG, "ensureCameraSessionInitialized error: ${t.message}", t)
            showStreamOverlay(
                "Gagal menyiapkan modul video kamera: ${t.message}",
                showSpinner = false,
                showRetry = true,
                diagText = "ID: ${devId.take(8)} • Inisialisasi P2P gagal"
            )
            return false
        }
    }

    private fun ensureDeviceAndConnect(forceReconnect: Boolean = false) {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) {
            showStreamOverlay("Kamera belum terhubung ke perangkat fisik Tuya.", showSpinner = false, showRetry = false)
            return
        }

        ThinkTechApp.ensureP2pUserSessionInitialized()

        if (forceReconnect) {
            hasVerifiedCloudMembership = false
            disconnectAndReconnect()
            return
        }

        val devBean = try {
            ThingHomeSdk.getDataInstance().getDeviceBean(devId)
        } catch (_: Throwable) {
            null
        }

        // shortcut: skips getHomeDetail whenever DeviceBean+localKey exist because Tuya HomeCacheModel uses DiscardOldestPolicy that can drop callbacks after pairing, rely on CameraBusiness.requestCameraInfo for cloud check
        val needsHomeSync = devBean == null || devBean.localKey.isNullOrBlank()

        if (needsHomeSync) {
            if (isFetchingHomeDetail) return
            isFetchingHomeDetail = true
            showStreamOverlay(
                "Memuat data perangkat dari Tuya Cloud...",
                showSpinner = true,
                showRetry = false,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Memulihkan cache perangkat"
            )
            mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
            mainHandler.postDelayed(homeSyncTimeoutRunnable, HOME_SYNC_TIMEOUT_MS)
            TuyaDeviceRepository.ensureHomeId(
                onReady = { homeId ->
                    val homeInst = runCatching { ThingHomeSdk.newHomeInstance(homeId) }.getOrNull()
                    activeHomeSyncInstance = homeInst
                    if (homeInst == null) {
                        runOnUiThread {
                            mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
                            isFetchingHomeDetail = false
                            showStreamOverlay(
                                "Gagal menginisialisasi sesi rumah Tuya.",
                                showSpinner = false,
                                showRetry = true,
                                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • HomeInstance null"
                            )
                        }
                        return@ensureHomeId
                    }
                    homeInst.getHomeDetail(object : IThingHomeResultCallback {
                        override fun onSuccess(bean: HomeBean?) {
                            runOnUiThread {
                                mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
                                isFetchingHomeDetail = false
                                hasVerifiedCloudMembership = true
                                if (isFinishing || isDestroyed) return@runOnUiThread

                                val cloudDevice = bean?.deviceList?.firstOrNull { it?.devId == devId }
                                if (cloudDevice != null) {
                                    isFromPairing = false
                                }
                                if (bean != null && cloudDevice == null && !isFromPairing) {
                                    isCameraUnboundFromCloud = true
                                    TuyaDeviceRepository.evictStaleCustomDevice(devId)
                                    updateHeaderStatus()
                                    showStreamOverlay(
                                        "Kamera ini sudah tidak terdaftar di akun ThinkTech (kemungkinan sudah di-pair ke aplikasi Tuya / Smart Life lain).",
                                        showSpinner = false,
                                        showRetry = true,
                                        retryLabel = "Pair Ulang Kamera",
                                        diagText = "$APP_DIAG_VER • ID ${devId.take(8)} dilepas dari Cloud • 1 kamera hanya aktif di 1 aplikasi"
                                    )
                                    return@runOnUiThread
                                }

                                val syncedBean = cloudDevice
                                    ?: ThingHomeSdk.getDataInstance().getDeviceBean(devId)
                                    ?: devBean
                                if (syncedBean == null) {
                                    isCameraUnboundFromCloud = true
                                    updateHeaderStatus()
                                    showStreamOverlay(
                                        "Data kamera tidak ditemukan di akun rumah ini. Silakan pair ulang kamera.",
                                        showSpinner = false,
                                        showRetry = true,
                                        retryLabel = "Pair Ulang Kamera",
                                        diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} tidak ada di daftar rumah"
                                    )
                                    return@runOnUiThread
                                }

                                isCameraUnboundFromCloud = false
                                isOnline = runCatching { syncedBean.isOnline }.getOrDefault(isOnline)
                                updateHeaderStatus()
                                syncInitialCameraDps()
                                if (ensureCameraSessionInitialized(syncedBean)) {
                                    connectAndPlay()
                                } else {
                                    showStreamOverlay(
                                        "Modul P2P kamera belum siap — ketuk Hubungkan Ulang.",
                                        showSpinner = false,
                                        showRetry = true,
                                        diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Native P2P belum siap"
                                    )
                                }
                            }
                        }

                        override fun onError(errorCode: String?, errorMsg: String?) {
                            runOnUiThread {
                                mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
                                isFetchingHomeDetail = false
                                hasVerifiedCloudMembership = true
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                val fallbackBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId) ?: devBean
                                if (fallbackBean != null && ensureCameraSessionInitialized(fallbackBean)) {
                                    connectAndPlay(connectMode = IThingSmartCameraP2P.CONNECT_MODE_AUTO, allowModeFallback = false)
                                } else {
                                    showStreamOverlay(
                                        "Gagal memuat data rumah ($errorCode): $errorMsg",
                                        showSpinner = false,
                                        showRetry = true,
                                        diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Cloud Home Error: $errorCode"
                                    )
                                }
                            }
                        }
                    })
                },
                onError = { code, msg ->
                    runOnUiThread {
                        mainHandler.removeCallbacks(homeSyncTimeoutRunnable)
                        isFetchingHomeDetail = false
                        hasVerifiedCloudMembership = true
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (devBean != null && ensureCameraSessionInitialized(devBean)) {
                            connectAndPlay(connectMode = IThingSmartCameraP2P.CONNECT_MODE_AUTO, allowModeFallback = false)
                        } else {
                            showStreamOverlay(
                                "Gagal memuat sesi rumah ($code): $msg",
                                showSpinner = false,
                                showRetry = true,
                                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Home Session Error: $code"
                            )
                        }
                    }
                }
            )
            return
        }

        if (devBean == null) return
        isCameraUnboundFromCloud = false
        isOnline = runCatching { devBean.isOnline }.getOrDefault(isOnline)
        updateHeaderStatus()
        if (ensureCameraSessionInitialized(devBean)) {
            connectAndPlay()
        } else {
            showStreamOverlay(
                "Modul P2P kamera belum siap — ketuk Hubungkan Ulang.",
                showSpinner = false,
                showRetry = true,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Native P2P belum siap"
            )
        }
    }

    private fun connectAndPlay(
        connectMode: Int = IThingSmartCameraP2P.CONNECT_MODE_FROM_INTERNET,
        allowModeFallback: Boolean = true
    ) {
        val p2p = cameraP2P ?: return
        if (isPrivateModeOn) {
            cancelStreamWatchdogs()
            showStreamOverlay(
                "Mode Privasi aktif — Ketuk Buka Lensa Kamera untuk menampilkan video.",
                showSpinner = false,
                showRetry = true,
                retryLabel = "Buka Lensa Kamera"
            )
            return
        }

        if (isP2pConnected) {
            if (!isPreviewing) startLivePreview()
            return
        }
        if (isConnectingP2p || isReconnectingSession) return

        isConnectingP2p = true
        val modeLabel = if (connectMode == IThingSmartCameraP2P.CONNECT_MODE_FROM_INTERNET) "Cloud+LAN" else "AutoCache"
        showStreamOverlay(
            "Menghubungkan stream video P2P...",
            showSpinner = true,
            showRetry = false,
            diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Mode: $modeLabel"
        )
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        mainHandler.postDelayed(connectTimeoutRunnable, STREAM_WATCHDOG_MS)

        runCatching {
            if (ThingIPCSdk.getCameraInstance()?.isLowPowerDevice(devId) == true) {
                ThingIPCSdk.getDoorbell()?.wirelessWake(devId)
            }
        }

        try {
            p2p.connect(devId, connectMode, object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    isConnectingP2p = false
                    isP2pConnected = true
                    runOnUiThread {
                        mainHandler.removeCallbacks(connectTimeoutRunnable)
                        if (isFinishing || isDestroyed || isPrivateModeOn) return@runOnUiThread
                        runCatching { binding.cameraVideoView.onResume() }
                        val monitor = boundMonitorView ?: runCatching { binding.cameraVideoView.createdView() }.getOrNull()
                        if (monitor != null) {
                            boundMonitorView = monitor
                            isVideoSurfaceReady = true
                            p2p.generateCameraView(monitor)
                        }
                        if (isVideoSurfaceReady) {
                            startLivePreview()
                        } else {
                            binding.cameraVideoView.postDelayed({
                                if (!isFinishing && !isDestroyed && isP2pConnected && !isPreviewing && !isPrivateModeOn) {
                                    startLivePreview()
                                }
                            }, 300L)
                        }
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    isConnectingP2p = false
                    isP2pConnected = false
                    Log.e(TAG, "P2P connect failed (mode=$connectMode): errCode=$errCode")
                    runOnUiThread {
                        cancelStreamWatchdogs()
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (errCode in -60100..-60000) {
                            isCameraUnboundFromCloud = true
                            TuyaDeviceRepository.evictStaleCustomDevice(devId)
                            updateHeaderStatus()
                            showStreamOverlay(
                                "Sesi kamera ditolak server Tuya (Kode $errCode) — kamera kemungkinan sudah di-pair ke aplikasi lain.",
                                showSpinner = false,
                                showRetry = true,
                                retryLabel = "Pair Ulang Kamera",
                                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Unbound ($errCode)"
                            )
                            return@runOnUiThread
                        }
                        if (allowModeFallback && connectMode != IThingSmartCameraP2P.CONNECT_MODE_AUTO) {
                            connectAndPlay(
                                connectMode = IThingSmartCameraP2P.CONNECT_MODE_AUTO,
                                allowModeFallback = false
                            )
                            return@runOnUiThread
                        }
                        showStreamOverlay(
                            "Gagal terhubung ke kamera (Kode $errCode)",
                            showSpinner = false,
                            showRetry = true,
                            diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Err: $errCode"
                        )
                    }
                }
            })
        } catch (t: Throwable) {
            isConnectingP2p = false
            cancelStreamWatchdogs()
            showStreamOverlay(
                "Kesalahan koneksi P2P: ${t.message}",
                showSpinner = false,
                showRetry = true,
                diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • Exception saat connect"
            )
        }
    }

    private fun startLivePreview(allowClarityFallback: Boolean = true) {
        val p2p = cameraP2P ?: return
        runCatching { binding.cameraVideoView.onResume() }
        val monitor = boundMonitorView ?: runCatching { binding.cameraVideoView.createdView() }.getOrNull()
        if (monitor != null) {
            boundMonitorView = monitor
            p2p.generateCameraView(monitor)
        }
        val clarityName = if (currentClarity == ThingIPCConstant.DEFINITION_HD) "HD" else "SD"
        showStreamOverlay(
            "Memulai tayangan langsung ($clarityName)...",
            showSpinner = true,
            showRetry = false,
            diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Membuka stream $clarityName"
        )
        mainHandler.removeCallbacks(previewTimeoutRunnable)
        mainHandler.postDelayed(previewTimeoutRunnable, STREAM_WATCHDOG_MS)
        try {
            p2p.startPreview(currentClarity, object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    isPreviewing = true
                    runOnUiThread {
                        cancelStreamWatchdogs()
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        binding.layoutStreamOverlay.visibility = View.GONE
                        updateClarityUi()
                        p2p.setMute(if (isMuted) ICameraP2P.MUTE else ICameraP2P.UNMUTE, object : OperationDelegateCallBack {
                            override fun onSuccess(s: Int, r: Int, d: String?) {}
                            override fun onFailure(s: Int, r: Int, e: Int) {}
                        })
                        consumePendingVoiceCameraAction()
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    isPreviewing = false
                    Log.e(TAG, "startPreview failed: clarity=$currentClarity errCode=$errCode")
                    runOnUiThread {
                        mainHandler.removeCallbacks(previewTimeoutRunnable)
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (allowClarityFallback) {
                            currentClarity = fallbackClarity(currentClarity)
                            updateClarityUi()
                            startLivePreview(allowClarityFallback = false)
                            return@runOnUiThread
                        }
                        showStreamOverlay(
                            "Tayangan gagal dimuat (Kode $errCode)",
                            showSpinner = false,
                            showRetry = true,
                            diagText = "$APP_DIAG_VER • ID: ${devId.take(8)} • P2P V$activeP2pProvider • Preview Err: $errCode"
                        )
                    }
                }
            })
        } catch (t: Throwable) {
            cancelStreamWatchdogs()
            showStreamOverlay(
                "Gagal memutar video: ${t.message}",
                showSpinner = false,
                showRetry = true,
                diagText = "ID: ${devId.take(8)} • Exception saat startPreview"
            )
        }
    }

    private fun showStreamOverlay(
        message: String,
        showSpinner: Boolean,
        showRetry: Boolean,
        retryLabel: String = "Hubungkan Ulang",
        diagText: String? = null
    ) {
        binding.layoutStreamOverlay.visibility = View.VISIBLE
        binding.tvStreamStatus.text = message
        if (diagText.isNullOrBlank()) {
            binding.tvStreamDiag.visibility = View.GONE
        } else {
            binding.tvStreamDiag.text = diagText
            binding.tvStreamDiag.visibility = View.VISIBLE
        }
        binding.pbStreamLoading.visibility = if (showSpinner) View.VISIBLE else View.GONE
        binding.btnRetryStream.text = retryLabel
        binding.btnRetryStream.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun applyPrivacyModeState(enabled: Boolean, triggerReconnect: Boolean) {
        isPrivateModeOn = enabled
        if (enabled) {
            cancelStreamWatchdogs()
            isReconnectingSession = false
            isConnectingP2p = false
            isP2pConnected = false
            isPreviewing = false
            runCatching { cameraP2P?.stopPreview(null) }
            runCatching { cameraP2P?.disconnect(null) }
            updateClarityUi()
            showStreamOverlay(
                "Mode Privasi aktif — Ketuk Buka Lensa Kamera untuk menampilkan video.",
                showSpinner = false,
                showRetry = true,
                retryLabel = "Buka Lensa Kamera"
            )
        } else {
            updateClarityUi()
            if (triggerReconnect) {
                showStreamOverlay("Menghubungkan stream video P2P...", showSpinner = true, showRetry = false)
                ensureDeviceAndConnect(forceReconnect = true)
            }
        }
    }

    private fun isPrivacyDpSupported(devBean: DeviceBean? = null): Boolean {
        val helperSupported = runCatching { dpHelper?.querySupportByDPCode("basic_private") == true }.getOrDefault(false)
        if (helperSupported) return true
        val bean = devBean ?: runCatching { ThingHomeSdk.getDataInstance().getDeviceBean(devId) }.getOrNull()
        val schemaMap = bean?.schemaMap
        if (schemaMap.isNullOrEmpty()) return false
        return schemaMap.values.any { it.code.equals("basic_private", ignoreCase = true) }
    }

    private fun syncPrivacyRowSupport(devBean: DeviceBean?) {
        val schemaLoaded = !devBean?.schemaMap.isNullOrEmpty()
        if (schemaLoaded && !isPrivacyDpSupported(devBean)) {
            binding.rowPrivateMode.visibility = View.GONE
            binding.dividerPrivateMode.visibility = View.GONE
            if (isPrivateModeOn || binding.switchPrivateMode.isChecked) {
                isSuppressingSwitchCallbacks = true
                binding.switchPrivateMode.isChecked = false
                isSuppressingSwitchCallbacks = false
                isPrivateModeOn = false
            }
        } else {
            binding.rowPrivateMode.visibility = View.VISIBLE
            binding.dividerPrivateMode.visibility = View.VISIBLE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                navigateBack()
            }
        }

        binding.btnCameraOptions.setOnClickListener {
            showCameraOptionsMenu()
        }

        binding.btnRetryStream.setOnClickListener {
            when {
                isCameraUnboundFromCloud -> {
                    val pairIntent = Intent(this, PairDeviceActivity::class.java).apply {
                        putExtra("HOME_ID", TuyaDeviceRepository.currentHomeId)
                    }
                    startActivity(pairIntent)
                    finish()
                }
                isPrivateModeOn -> {
                    isSuppressingSwitchCallbacks = true
                    binding.switchPrivateMode.isChecked = false
                    isSuppressingSwitchCallbacks = false
                    if (isPrivacyDpSupported()) {
                        publishCameraDp("basic_private", null, false, "Mode Privasi nonaktif")
                    }
                    applyPrivacyModeState(enabled = false, triggerReconnect = true)
                }
                else -> {
                    ensureDeviceAndConnect(forceReconnect = true)
                }
            }
        }

        // Clarity HD / SD Toggle
        binding.btnToggleClarity.setOnClickListener {
            currentClarity = fallbackClarity(currentClarity)
            updateClarityUi()
            if (isP2pConnected) {
                startLivePreview(allowClarityFallback = false)
            }
        }

        // Fullscreen Landscape / Portrait Toggle
        binding.btnFullscreen.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        // 1. Speaker Mute / Unmute
        binding.btnAudioMute.setOnClickListener {
            val p2p = cameraP2P
            if (p2p == null || !isPreviewing) {
                Toast.makeText(this, "Tunggu tayangan live aktif terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nextMute = if (isMuted) ICameraP2P.UNMUTE else ICameraP2P.MUTE
            p2p.setMute(nextMute, object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    isMuted = (nextMute == ICameraP2P.MUTE)
                    runOnUiThread {
                        updateAudioMuteUi()
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    runOnUiThread {
                        Toast.makeText(this@CameraControlActivity, "Gagal mengatur audio ($errCode)", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        // 2. Two-Way Audio Talk
        binding.btnTwoWayTalk.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleTwoWayTalk()
            }
        }

        // 3. Snapshot
        binding.btnSnapshot.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            binding.flSnapshotCircle.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80L).withEndAction {
                binding.flSnapshotCircle.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }.start()
            val p2p = cameraP2P
            if (p2p == null || !isPreviewing) {
                Toast.makeText(this, "Tayangan live belum aktif", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ThinkTechCam").apply { mkdirs() }
            p2p.snapshot(dir.absolutePath, this, object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    runOnUiThread {
                        Toast.makeText(this@CameraControlActivity, "Tangkapan layar tersimpan: ${data ?: dir.name}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    runOnUiThread {
                        Toast.makeText(this@CameraControlActivity, "Gagal mengambil potret ($errCode)", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        // 4. Record Local MP4
        binding.btnRecord.setOnClickListener {
            val p2p = cameraP2P
            if (p2p == null || !isPreviewing) {
                Toast.makeText(this, "Tayangan live belum aktif", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isRecording) {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ThinkTechCam").apply { mkdirs() }
                p2p.startRecordLocalMp4(dir.absolutePath, this, object : OperationDelegateCallBack {
                    override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                        isRecording = true
                        runOnUiThread {
                            updateRecordUi()
                            Toast.makeText(this@CameraControlActivity, "Perekaman video dimulai...", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                        runOnUiThread {
                            Toast.makeText(this@CameraControlActivity, "Gagal memulai rekaman ($errCode)", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } else {
                p2p.stopRecordLocalMp4(object : OperationDelegateCallBack {
                    override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                        isRecording = false
                        runOnUiThread {
                            updateRecordUi()
                            Toast.makeText(this@CameraControlActivity, "Video berhasil disimpan!", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                        isRecording = false
                        runOnUiThread {
                            updateRecordUi()
                        }
                    }
                })
            }
        }

        // PTZ Touch & Hold Controls (Huawei Sculpted Dial Wheel)
        bindPtzButton(binding.btnPtzUp, binding.ivPtzUp, ThingIPCConstant.THING_PTZ_UP)
        bindPtzButton(binding.btnPtzDown, binding.ivPtzDown, ThingIPCConstant.THING_PTZ_DOWN)
        bindPtzButton(binding.btnPtzLeft, binding.ivPtzLeft, ThingIPCConstant.THING_PTZ_LEFT)
        bindPtzButton(binding.btnPtzRight, binding.ivPtzRight, ThingIPCConstant.THING_PTZ_RIGHT)
        binding.btnPtzStop.setOnClickListener { v ->
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }.start()
            stopPtzMove()
        }

        // Camera Feature Switches
        binding.switchMotionDetect.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingSwitchCallbacks) return@setOnCheckedChangeListener
            publishCameraDp("motion_switch", "134", isChecked, "Deteksi gerakan ${if (isChecked) "aktif" else "nonaktif"}")
        }

        binding.switchMotionTrack.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingSwitchCallbacks) return@setOnCheckedChangeListener
            publishCameraDp(PTZDPModel.DP_MOTION_TRACKING, "161", isChecked, "Pelacakan objek ${if (isChecked) "aktif" else "nonaktif"}")
        }

        binding.rowNightVision.setOnClickListener {
            val nextMode = when (currentNightVision) {
                "0" -> "2" // Auto -> IR On
                "2" -> "1" // IR On -> Off
                else -> "0" // Off -> Auto
            }
            currentNightVision = nextMode
            updateNightVisionLabel(nextMode)
            publishCameraDp("basic_nightvision", "108", nextMode, "Mode Malam: ${binding.tvNightVisionMode.text}")
        }

        binding.switchPrivateMode.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingSwitchCallbacks) return@setOnCheckedChangeListener
            if (!isPrivacyDpSupported()) {
                isSuppressingSwitchCallbacks = true
                binding.switchPrivateMode.isChecked = false
                isSuppressingSwitchCallbacks = false
                isPrivateModeOn = false
                Toast.makeText(this, "Mode Privasi tidak didukung pada firmware kamera ini", Toast.LENGTH_SHORT).show()
                if (!isPreviewing) ensureDeviceAndConnect()
                return@setOnCheckedChangeListener
            }
            applyPrivacyModeState(enabled = isChecked, triggerReconnect = !isChecked)
            publishCameraDp(
                dpCode = "basic_private",
                fallbackDpId = null,
                value = isChecked,
                successToast = "Mode Privasi ${if (isChecked) "aktif" else "nonaktif"}",
                onErrorRollback = {
                    isSuppressingSwitchCallbacks = true
                    binding.switchPrivateMode.isChecked = !isChecked
                    isSuppressingSwitchCallbacks = false
                    applyPrivacyModeState(enabled = !isChecked, triggerReconnect = isChecked)
                }
            )
        }

        binding.switchFlipScreen.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingSwitchCallbacks) return@setOnCheckedChangeListener
            publishCameraDp("basic_flip", "103", isChecked, "Putar layar 180° ${if (isChecked) "aktif" else "nonaktif"}")
        }

        binding.switchIndicatorLed.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingSwitchCallbacks) return@setOnCheckedChangeListener
            publishCameraDp("basic_indicator", "101", isChecked, "Indikator LED ${if (isChecked) "aktif" else "nonaktif"}")
        }

        binding.btnSirenAlarm.setOnClickListener {
            isSirenActive = !isSirenActive
            binding.btnSirenAlarm.text = if (isSirenActive) "Matikan Sirene" else "Bunyikan Sirene"
            binding.btnSirenAlarm.setTextColor(Color.parseColor(if (isSirenActive) "#E84026" else "#D97706"))
            binding.btnSirenAlarm.backgroundTintList = if (isSirenActive) {
                ColorStateList.valueOf(Color.parseColor("#FFF1F0"))
            } else {
                null
            }
            publishCameraDp("siren_switch", "159", isSirenActive, if (isSirenActive) "Sirene kamera dibunyikan!" else "Sirene dimatikan")
        }

        binding.btnDeleteCamera.setOnClickListener {
            confirmDeleteCamera()
        }
    }

    private fun updateAudioMuteUi() {
        binding.ivAudioMute.setImageResource(
            if (isMuted) R.drawable.ic_cam_speaker_off else R.drawable.ic_cam_speaker_on
        )
        if (isMuted) {
            binding.flAudioMuteCircle.setBackgroundResource(R.drawable.bg_harmony_power_off)
            binding.ivAudioMute.imageTintList = ColorStateList.valueOf(Color.parseColor("#191919"))
            binding.tvAudioMute.text = "Suara"
            binding.tvAudioMute.setTextColor(Color.parseColor("#666B75"))
        } else {
            binding.flAudioMuteCircle.setBackgroundResource(R.drawable.bg_harmony_power_on)
            binding.ivAudioMute.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.tvAudioMute.text = "Suara Aktif"
            binding.tvAudioMute.setTextColor(Color.parseColor("#0A59F7"))
        }
    }

    private fun updateTwoWayTalkUi() {
        if (isTalking) {
            binding.flTalkCircle.setBackgroundResource(R.drawable.bg_harmony_cam_btn_emerald)
            binding.ivTwoWayTalk.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.tvTwoWayTalk.text = "Bicara Aktif"
            binding.tvTwoWayTalk.setTextColor(Color.parseColor("#00996B"))
        } else {
            binding.flTalkCircle.setBackgroundResource(R.drawable.bg_harmony_power_off)
            binding.ivTwoWayTalk.imageTintList = ColorStateList.valueOf(Color.parseColor("#191919"))
            binding.tvTwoWayTalk.text = "Bicara"
            binding.tvTwoWayTalk.setTextColor(Color.parseColor("#666B75"))
        }
    }

    private fun updateRecordUi() {
        binding.tvRecBadge.visibility = if (isRecording) View.VISIBLE else View.GONE
        if (isRecording) {
            binding.flRecordCircle.setBackgroundResource(R.drawable.bg_harmony_cam_btn_red)
            binding.ivRecord.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.tvRecord.text = "Merekam"
            binding.tvRecord.setTextColor(Color.parseColor("#E84026"))
        } else {
            binding.flRecordCircle.setBackgroundResource(R.drawable.bg_harmony_power_off)
            binding.ivRecord.imageTintList = ColorStateList.valueOf(Color.parseColor("#191919"))
            binding.tvRecord.text = "Rekam"
            binding.tvRecord.setTextColor(Color.parseColor("#666B75"))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindPtzButton(view: View, iconView: ImageView, directionCode: String) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#0A59F7"))
                    iconView.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90L).start()
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    startPtzMove(directionCode)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#191919"))
                    iconView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140L).start()
                    stopPtzMove()
                    true
                }
                else -> false
            }
        }
    }

    private fun consumePendingVoiceCameraAction() {
        val action = pendingVoiceCameraAction ?: return
        pendingVoiceCameraAction = null
        mainHandler.postDelayed({
            if (isFinishing || isDestroyed || !isPreviewing) return@postDelayed
            when (action) {
                "snapshot" -> binding.btnSnapshot.performClick()
                "record" -> if (!isRecording) binding.btnRecord.performClick()
                "talk" -> if (!isTalking) binding.btnTwoWayTalk.performClick()
                "unmute" -> if (isMuted) binding.btnAudioMute.performClick()
                "mute" -> if (!isMuted) binding.btnAudioMute.performClick()
            }
        }, 400L)
    }

    private fun startPtzMove(directionCode: String) {
        publishCameraDp(PTZDPModel.DP_PTZ_CONTROL, "119", directionCode, null)
    }

    private fun stopPtzMove() {
        publishCameraDp(PTZDPModel.DP_PTZ_STOP, "116", true, null)
    }

    private fun toggleTwoWayTalk() {
        val p2p = cameraP2P
        if (p2p == null || !isPreviewing) {
            Toast.makeText(this, "Tayangan live belum aktif", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isTalking) {
            // Unmute speaker so 2-way conversation works smoothly
            if (isMuted) {
                p2p.setMute(ICameraP2P.UNMUTE, object : OperationDelegateCallBack {
                    override fun onSuccess(s: Int, r: Int, d: String?) {
                        isMuted = false
                        runOnUiThread { updateAudioMuteUi() }
                    }
                    override fun onFailure(s: Int, r: Int, e: Int) {}
                })
            }
            p2p.startAudioTalk(object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    isTalking = true
                    runOnUiThread {
                        updateTwoWayTalkUi()
                        Toast.makeText(this@CameraControlActivity, "Mikrofon aktif — silakan bicara ke kamera", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    runOnUiThread {
                        Toast.makeText(this@CameraControlActivity, "Gagal mengaktifkan interkom ($errCode)", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            p2p.stopAudioTalk(object : OperationDelegateCallBack {
                override fun onSuccess(sessionId: Int, requestId: Int, data: String?) {
                    isTalking = false
                    runOnUiThread {
                        updateTwoWayTalkUi()
                    }
                }

                override fun onFailure(sessionId: Int, requestId: Int, errCode: Int) {
                    isTalking = false
                    runOnUiThread {
                        updateTwoWayTalkUi()
                    }
                }
            })
        }
    }

    private fun syncInitialCameraDps() {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) return
        try {
            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val dpsMap = devBean?.dps
            if (dpsMap != null) {
                syncUiFromRawDps(JSONObject(dpsMap))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "syncInitialCameraDps error: ${t.message}")
        }
    }

    private fun syncUiFromRawDps(dps: JSONObject?) {
        if (dps == null) return
        val wasPrivate = isPrivateModeOn
        var updatedPrivate: Boolean? = null
        isSuppressingSwitchCallbacks = true
        try {
            resolveDpValue(dps, "motion_switch", "134")?.toString()?.toBooleanStrictOrNull()?.let {
                binding.switchMotionDetect.isChecked = it
            }
            resolveDpValue(dps, PTZDPModel.DP_MOTION_TRACKING, "161")?.toString()?.toBooleanStrictOrNull()?.let {
                binding.switchMotionTrack.isChecked = it
            }
            resolveDpValue(dps, "basic_nightvision", "108")?.toString()?.let { mode ->
                currentNightVision = mode
                updateNightVisionLabel(mode)
            }

            val schemaCodeMap = buildSchemaCodeToIdMap()
            resolveStrictPrivacyMode(dps, schemaCodeMap)?.let {
                updatedPrivate = it
                isPrivateModeOn = it
                binding.switchPrivateMode.isChecked = it
            }

            resolveDpValue(dps, "basic_flip", "103")?.toString()?.toBooleanStrictOrNull()?.let {
                binding.switchFlipScreen.isChecked = it
            }
            resolveDpValue(dps, "basic_indicator", "101")?.toString()?.toBooleanStrictOrNull()?.let {
                binding.switchIndicatorLed.isChecked = it
            }
        } finally {
            isSuppressingSwitchCallbacks = false
        }

        if (updatedPrivate != null && updatedPrivate != wasPrivate) {
            applyPrivacyModeState(enabled = updatedPrivate == true, triggerReconnect = updatedPrivate == false)
        }
    }

    private fun buildSchemaCodeToIdMap(): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching {
            val helperId = dpHelper?.getDPIdByCode("basic_private")?.takeIf { it.isNotBlank() }
            if (helperId != null && dpHelper?.querySupportByDPCode("basic_private") == true) {
                map["basic_private"] = helperId
            }
            ThingHomeSdk.getDataInstance().getDeviceBean(devId)?.schemaMap?.forEach { (dpId, schema) ->
                val code = schema?.code
                if (!dpId.isNullOrBlank() && !code.isNullOrBlank()) {
                    map[code] = dpId
                }
            }
        }
        return map
    }

    private fun resolveDpValue(dps: JSONObject, dpCode: String, fallbackDpId: String?): Any? {
        val devBean = runCatching { ThingHomeSdk.getDataInstance().getDeviceBean(devId) }.getOrNull()
        val mappedId = try {
            dpHelper?.getDPIdByCode(dpCode)?.takeIf { it.isNotBlank() }
                ?: devBean?.schemaMap?.entries
                    ?.firstOrNull { it.value.code.equals(dpCode, ignoreCase = true) }?.key
        } catch (_: Throwable) {
            null
        }
        if (mappedId != null) {
            return if (dps.containsKey(mappedId)) dps[mappedId] else null
        }
        if (!devBean?.schemaMap.isNullOrEmpty()) {
            return null
        }
        return if (!fallbackDpId.isNullOrBlank() && dps.containsKey(fallbackDpId)) dps[fallbackDpId] else null
    }

    private fun updateNightVisionLabel(mode: String) {
        binding.tvNightVisionMode.text = when (mode) {
            "1" -> "Mati"
            "2" -> "Aktif (IR)"
            else -> "Otomatis"
        }
    }

    private fun publishCameraDp(
        dpCode: String,
        fallbackDpId: String?,
        value: Any,
        successToast: String?,
        onErrorRollback: (() -> Unit)? = null
    ) {
        if (!ThinkTechApp.isSdkInitialized || devId.isBlank() || devId.startsWith("demo_")) return

        val callback = object : IResultCallback {
            override fun onSuccess() {
                if (!successToast.isNullOrBlank()) {
                    runOnUiThread {
                        Toast.makeText(this@CameraControlActivity, successToast, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(code: String?, error: String?) {
                Log.w(TAG, "Camera DP ($dpCode/$fallbackDpId) error: $code - $error")
                runOnUiThread {
                    onErrorRollback?.invoke()
                    if (!successToast.isNullOrBlank()) {
                        Toast.makeText(
                            this@CameraControlActivity,
                            "Fitur belum didukung pada firmware kamera ini ($code)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        try {
            val helper = dpHelper
            if (helper != null && helper.querySupportByDPCode(dpCode)) {
                helper.publishDps(dpCode, value, callback)
                return
            }

            val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
            val schemaDpId = devBean?.schemaMap?.entries
                ?.firstOrNull { it.value.code.equals(dpCode, ignoreCase = true) }?.key
            val dpId = schemaDpId ?: if (devBean?.schemaMap.isNullOrEmpty()) fallbackDpId else null
            if (dpId.isNullOrBlank()) {
                onErrorRollback?.invoke()
                if (!successToast.isNullOrBlank()) {
                    Toast.makeText(this, "Fitur ini tidak tersedia pada model kamera Anda", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val json = JSONObject().apply { put(dpId, value) }
            ThingHomeSdk.newDeviceInstance(devId).publishDps(json.toJSONString(), callback)
        } catch (t: Throwable) {
            Log.e(TAG, "publishCameraDp exception: ${t.message}")
            onErrorRollback?.invoke()
        }
    }

    private fun showCameraOptionsMenu() {
        val options = arrayOf("Ubah Nama Kamera", "Segarkan Koneksi Video", "Hapus Kamera dari Rumah")
        AlertDialog.Builder(this)
            .setTitle(devName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog()
                    1 -> {
                        ensureDeviceAndConnect(forceReconnect = true)
                    }
                    2 -> confirmDeleteCamera()
                }
            }
            .show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply {
            setText(devName)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Ubah Nama Kamera")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    devName = newName
                    binding.tvCameraTitle.text = newName
                    TuyaDeviceRepository.renameDevice(devId, newName)
                    Toast.makeText(this, "Nama kamera berhasil diubah", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteCamera() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Kamera")
            .setMessage("Apakah Anda yakin ingin menghapus '$devName' dari rumah?")
            .setPositiveButton("Hapus") { _, _ ->
                deviceRepo.removeDevice(
                    devId = devId,
                    onSuccess = {
                        runOnUiThread {
                            Toast.makeText(this, "Kamera '$devName' berhasil dihapus", Toast.LENGTH_SHORT).show()
                            navigateBack()
                        }
                    },
                    onError = { code, err ->
                        runOnUiThread {
                            Toast.makeText(this, "Gagal menghapus kamera ($code): $err", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val density = resources.displayMetrics.density
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        syncSystemBars(isLandscape = isLandscape)
        applyVideoContainerRadius(isFullscreen = isLandscape)
        if (isLandscape) {
            binding.topBar.visibility = View.GONE
            binding.layoutMediaDock.visibility = View.GONE
            binding.scrollControls.visibility = View.GONE
            binding.flVideoContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.layoutMediaDock.visibility = View.VISIBLE
            binding.scrollControls.visibility = View.VISIBLE
            binding.flVideoContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (226 * density).toInt()
            ).apply {
                val marginH = (16 * density).toInt()
                setMargins(marginH, (8 * density).toInt(), marginH, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            binding.cameraVideoView.onResume()
        } catch (_: Throwable) {}
        ensureDeviceAndConnect()
    }

    override fun onPause() {
        super.onPause()
        cancelStreamWatchdogs()
        isReconnectingSession = false
        try {
            if (isTalking) {
                cameraP2P?.stopAudioTalk(null)
                isTalking = false
                updateTwoWayTalkUi()
            }
            if (isRecording) {
                cameraP2P?.stopRecordLocalMp4(null)
                isRecording = false
                updateRecordUi()
            }
            if (isPreviewing) {
                cameraP2P?.stopPreview(null)
                isPreviewing = false
            }
            if (isConnectingP2p) {
                detachStuckCameraP2p()
            } else if (isP2pConnected) {
                cameraP2P?.disconnect(null)
            }
            isConnectingP2p = false
            isP2pConnected = false
            updateClarityUi()
            binding.cameraVideoView.onPause()
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyCameraP2pFinal()
        boundMonitorView = null
        runCatching { activeHomeSyncInstance?.onDestroy() }
        activeHomeSyncInstance = null

        try {
            dpHelper?.removeWeakRefDevListener(devListener)
            dpHelper?.onDestroy()
        } catch (_: Throwable) {}
        dpHelper = null
    }

    private fun navigateBack() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}

