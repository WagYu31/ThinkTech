package com.thinktech.smarthome.ui.pairing

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.data.TuyaPairingRepository
import com.thinktech.smarthome.data.model.SmartDeviceItem
import com.thinktech.smarthome.databinding.ActivityConnectingDeviceBinding
import com.thinktech.smarthome.databinding.DialogPairTuyaDeviceBinding
import com.thinktech.smarthome.ui.home.MainActivity

class ConnectingDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectingDeviceBinding
    private val handler = Handler(Looper.getMainLooper())
    private val pairingRepo = TuyaPairingRepository()

    private var deviceName: String = "WiFi 3ch switch"
    private var deviceIconRes: Int = R.drawable.ic_tuya_wifi_3ch_switch
    private var deviceCategory: String = "Electrical"
    private var deviceSubcategory: String = "Switch"
    private var homeId: Long = 0L
    private var scanUuid: String = ""
    private var wifiSsid: String = ""
    private var wifiPass: String = ""
    private var isSingleBleOnly: Boolean = false
    private var autoStartPairing: Boolean = false

    private var ringAnimator: ValueAnimator? = null
    private var deviceFloatAnimator: ObjectAnimator? = null
    private var smoothCrawlRunnable: Runnable? = null
    private var currentProgress: Float = 0f

    private var isPairingInProgress = false
    private var isPairingCompleted = false
    private var connectedDevice: SmartDeviceItem? = null

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_ICON = "extra_device_icon"
        const val EXTRA_DEVICE_CATEGORY = "extra_device_category"
        const val EXTRA_DEVICE_SUBCATEGORY = "extra_device_subcategory"
        const val EXTRA_HOME_ID = "extra_home_id"
        const val EXTRA_CONNECTION_TYPE = "extra_connection_type"
        const val EXTRA_SCAN_UUID = "extra_scan_uuid"
        const val EXTRA_WIFI_SSID = "extra_wifi_ssid"
        const val EXTRA_WIFI_PASS = "extra_wifi_pass"
        const val EXTRA_IS_SINGLE_BLE = "extra_is_single_ble"
        const val EXTRA_AUTO_START = "extra_auto_start"
        const val EXTRA_PREVIEW_MODE = "extra_preview_mode"

        fun start(
            context: Context,
            name: String,
            iconRes: Int,
            category: String,
            subcategory: String,
            homeId: Long = 0L,
            connectionType: String = "Ethernet"
        ): Intent {
            return Intent(context, ConnectingDeviceActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, name)
                putExtra(EXTRA_DEVICE_ICON, iconRes)
                putExtra(EXTRA_DEVICE_CATEGORY, category)
                putExtra(EXTRA_DEVICE_SUBCATEGORY, subcategory)
                putExtra(EXTRA_HOME_ID, homeId)
                putExtra(EXTRA_CONNECTION_TYPE, connectionType)
                putExtra(EXTRA_AUTO_START, false)
            }
        }

        fun startWithCredentials(
            context: Context,
            name: String,
            iconRes: Int,
            category: String,
            subcategory: String,
            homeId: Long,
            scanUuid: String,
            ssid: String,
            pass: String,
            isSingleBleOnly: Boolean = false
        ): Intent {
            return Intent(context, ConnectingDeviceActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, name)
                putExtra(EXTRA_DEVICE_ICON, iconRes)
                putExtra(EXTRA_DEVICE_CATEGORY, category)
                putExtra(EXTRA_DEVICE_SUBCATEGORY, subcategory)
                putExtra(EXTRA_HOME_ID, homeId)
                putExtra(EXTRA_SCAN_UUID, scanUuid)
                putExtra(EXTRA_WIFI_SSID, ssid)
                putExtra(EXTRA_WIFI_PASS, pass)
                putExtra(EXTRA_IS_SINGLE_BLE, isSingleBleOnly)
                putExtra(EXTRA_AUTO_START, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectingDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCleanWhiteChrome()

        deviceName = (intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "WiFi 3ch switch").replace("\n", " ")
        deviceIconRes = intent.getIntExtra(EXTRA_DEVICE_ICON, R.drawable.ic_tuya_wifi_3ch_switch)
        deviceCategory = intent.getStringExtra(EXTRA_DEVICE_CATEGORY) ?: "Electrical"
        deviceSubcategory = intent.getStringExtra(EXTRA_DEVICE_SUBCATEGORY) ?: "Switch"
        homeId = intent.getLongExtra(EXTRA_HOME_ID, 0L)
        scanUuid = intent.getStringExtra(EXTRA_SCAN_UUID).orEmpty()
        wifiSsid = intent.getStringExtra(EXTRA_WIFI_SSID).orEmpty()
        wifiPass = intent.getStringExtra(EXTRA_WIFI_PASS).orEmpty()
        isSingleBleOnly = intent.getBooleanExtra(EXTRA_IS_SINGLE_BLE, false)
        autoStartPairing = intent.getBooleanExtra(EXTRA_AUTO_START, false)

        setupViews()

        val previewMode = intent.getStringExtra(EXTRA_PREVIEW_MODE).orEmpty()
        when (previewMode) {
            "connecting" -> {
                showConnectingVisual(initialDetail = "Mengirim kredensial Wi-Fi ke perangkat...")
                animateRingTo(75f, 650L)
            }
            "connected" -> {
                val demoItem = SmartDeviceItem(
                    devId = "demo_switch_01",
                    name = deviceName,
                    isOnline = true,
                    isSwitchOn = true,
                    productId = "tt_switch_v1",
                    category = deviceCategory
                )
                connectedDevice = demoItem
                isPairingCompleted = true
                showConnectedCelebration(deviceName, animateEntrance = false)
            }
            else -> {
                if (autoStartPairing && (isSingleBleOnly || wifiSsid.isNotBlank())) {
                    beginLiveTuyaPairing()
                } else {
                    showReadyToConnectState()
                    handler.postDelayed({
                        if (!isFinishing && !isDestroyed && !isPairingInProgress && !isPairingCompleted) {
                            showWifiInputDialog()
                        }
                    }, 320L)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitAttempt()
            }
        })
    }

    private fun setupCleanWhiteChrome() {
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.btnControlDevice.outlineSpotShadowColor = 0x4D3858FF
            binding.btnControlDevice.outlineAmbientShadowColor = 0x1F3858FF
        }
    }

    private fun setupViews() {
        binding.tvConnectingDeviceName.text = deviceName
        binding.ivConnectingDeviceIcon.setImageResource(deviceIconRes)
        binding.ivConnectedDeviceIcon.setImageResource(deviceIconRes)
        binding.tvConnectedSubtitle.text = "You have connected to $deviceName"

        binding.btnBack.setOnClickListener { handleExitAttempt() }
        binding.btnMoreConnecting.setOnClickListener { showMoreOptionsMenu() }
        binding.tvLearnMoreConnecting.setOnClickListener { showHelpDialog() }

        binding.btnChangeWifi.setOnClickListener {
            pairingRepo.stopPairing()
            stopProgressCrawl()
            isPairingInProgress = false
            showWifiInputDialog()
        }

        binding.btnRetryOrConnect.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isSingleBleOnly || wifiSsid.isNotBlank()) {
                beginLiveTuyaPairing()
            } else {
                showWifiInputDialog()
            }
        }

        binding.btnGoToHomepage.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setResult(Activity.RESULT_OK)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        binding.btnControlDevice.setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setResult(Activity.RESULT_OK)
            val dev = connectedDevice
            if (dev != null) {
                TuyaDeviceClassifier.openDeviceControl(
                    context = this,
                    devId = dev.devId,
                    name = dev.name,
                    category = dev.category,
                    productId = dev.productId,
                    isOnline = true,
                    isSwitchOn = true,
                    isFromPairing = true
                )
            }
            finish()
        }
    }

    private fun showReadyToConnectState() {
        binding.layoutConnectingState.visibility = View.VISIBLE
        binding.layoutConnectedState.visibility = View.GONE
        binding.layoutProgressReadout.visibility = View.GONE
        binding.layoutRetryActions.visibility = View.VISIBLE
        binding.btnChangeWifi.visibility = View.GONE
        binding.btnRetryOrConnect.text = "Connect"
        binding.connectRingView.setProgress(0f)
    }

    private fun showConnectingVisual(initialDetail: String) {
        binding.layoutConnectingState.visibility = View.VISIBLE
        binding.layoutConnectedState.visibility = View.GONE
        binding.layoutProgressReadout.visibility = View.VISIBLE
        binding.layoutRetryActions.visibility = View.GONE
        binding.tvConnectingStatusLabel.text = "Connecting..."
        binding.tvConnectingStatusLabel.setTextColor(Color.parseColor("#666B75"))
        binding.tvConnectingPercent.setTextColor(Color.parseColor("#3858FF"))
        binding.tvConnectingStepDetail.text = initialDetail
        startDeviceFloatAnimation()
    }

    private fun beginLiveTuyaPairing() {
        pairingRepo.stopPairing()
        stopProgressCrawl()
        isPairingInProgress = true
        isPairingCompleted = false
        currentProgress = 0f
        binding.connectRingView.setProgress(0f)
        binding.tvConnectingPercent.text = "0%"

        val initialMsg = if (isSingleBleOnly) {
            "Menghubungkan perangkat melalui Bluetooth..."
        } else {
            "Mengirim kredensial Wi-Fi $wifiSsid ke perangkat..."
        }
        showConnectingVisual(initialMsg)
        animateRingTo(18f, 700L)
        startSmoothProgressCrawl()

        val effectiveHomeId = if (homeId != 0L) homeId else TuyaDeviceRepository.currentHomeId
        val matchedScanBean = (if (scanUuid.isNotBlank()) TuyaPairingRepository.scannedBeansByUuid[scanUuid] else null)
            ?: TuyaPairingRepository.findMatchingScannedBean(deviceName, deviceCategory)

        val onFound: (DeviceBean) -> Unit = { devResp ->
            val resolvedDevName = if (!devResp.name.isNullOrBlank() && !devResp.name.equals("null", true)) {
                devResp.name
            } else {
                deviceName
            }
            val defaultPid = if (deviceCategory.contains("Kamera", true) || resolvedDevName.contains("Camera", true)) {
                "tt_ipc_v1"
            } else {
                "tt_plug_v1"
            }
            val resolvedPid = when {
                !devResp.productId.isNullOrBlank() -> devResp.productId
                !matchedScanBean?.productId.isNullOrBlank() -> matchedScanBean!!.productId
                else -> defaultPid
            }
            val newDev = TuyaDeviceRepository.addCustomDevice(
                name = resolvedDevName,
                category = deviceCategory,
                productId = resolvedPid,
                explicitDevId = devResp.devId
            )

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pairingRepo.stopPairing()
                stopProgressCrawl()
                isPairingInProgress = false
                isPairingCompleted = true
                connectedDevice = newDev
                deviceName = resolvedDevName

                binding.tvConnectingStepDetail.text = "Perangkat berhasil terverifikasi!"
                animateRingTo(100f, 420L) {
                    if (!isFinishing && !isDestroyed) {
                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                showConnectedCelebration(resolvedDevName, animateEntrance = true)
                            }
                        }, 240L)
                    }
                }
            }
        }

        val onErr: (String, String) -> Unit = { code, error ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pairingRepo.stopPairing()
                stopProgressCrawl()
                stopDeviceFloatAnimation()
                isPairingInProgress = false

                binding.tvConnectingStatusLabel.text = "Gagal Menghubungkan"
                binding.tvConnectingStatusLabel.setTextColor(Color.parseColor("#E84026"))
                binding.tvConnectingPercent.setTextColor(Color.parseColor("#E84026"))
                binding.tvConnectingStepDetail.text = "($code) $error"
                binding.layoutRetryActions.visibility = View.VISIBLE
                binding.btnChangeWifi.visibility = if (isSingleBleOnly) View.GONE else View.VISIBLE
                binding.btnRetryOrConnect.text = "Coba Lagi"
            }
        }

        val onStep: (String) -> Unit = { step ->
            runOnUiThread {
                if (isFinishing || isDestroyed || !isPairingInProgress) return@runOnUiThread
                binding.tvConnectingStepDetail.text = step
                val lower = step.lowercase()
                val milestone = when {
                    lower.contains("token") || lower.contains("meminta") -> 28f
                    lower.contains("kredensial") || lower.contains("mengirim") || lower.contains("wifi") -> 48f
                    lower.contains("mendaftarkan") || lower.contains("cloud") || lower.contains("aktivasi") -> 72f
                    lower.contains("terhubung") || lower.contains("online") -> 88f
                    else -> (currentProgress + 8f).coerceAtMost(86f)
                }
                if (milestone > currentProgress) {
                    animateRingTo(milestone, 900L)
                }
            }
        }

        if (matchedScanBean != null) {
            pairingRepo.startMultiModePairing(
                context = this,
                homeId = effectiveHomeId,
                scanDeviceBean = matchedScanBean,
                ssid = wifiSsid,
                pass = wifiPass,
                timeoutSec = 120,
                onDeviceFound = onFound,
                onError = onErr,
                onStepUpdate = onStep
            )
        } else {
            pairingRepo.startEzPairing(
                context = this,
                homeId = effectiveHomeId,
                ssid = wifiSsid,
                pass = wifiPass,
                timeoutSec = 100,
                onDeviceFound = onFound,
                onError = onErr,
                onStepUpdate = onStep
            )
        }
    }

    private fun startSmoothProgressCrawl() {
        stopProgressCrawl()
        smoothCrawlRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || !isPairingInProgress) return
                if (currentProgress < 92f) {
                    val step = when {
                        currentProgress < 45f -> 3.5f
                        currentProgress < 75f -> 2.0f
                        else -> 0.8f
                    }
                    animateRingTo((currentProgress + step).coerceAtMost(92f), 950L)
                }
                handler.postDelayed(this, 1100L)
            }
        }
        handler.postDelayed(smoothCrawlRunnable!!, 1200L)
    }

    private fun stopProgressCrawl() {
        smoothCrawlRunnable?.let { handler.removeCallbacks(it) }
        smoothCrawlRunnable = null
        ringAnimator?.cancel()
        ringAnimator = null
    }

    private fun animateRingTo(target: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        val clampedTarget = target.coerceIn(0f, 100f)
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(currentProgress, clampedTarget).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val v = va.animatedValue as Float
                currentProgress = v
                binding.connectRingView.setProgress(v)
                binding.tvConnectingPercent.text = "${v.toInt()}%"
            }
            if (onEnd != null) {
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun startDeviceFloatAnimation() {
        stopDeviceFloatAnimation()
        deviceFloatAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.ivConnectingDeviceIcon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f, 1.0f)
        ).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopDeviceFloatAnimation() {
        deviceFloatAnimator?.cancel()
        deviceFloatAnimator = null
        binding.ivConnectingDeviceIcon.scaleX = 1f
        binding.ivConnectingDeviceIcon.scaleY = 1f
    }

    private fun showConnectedCelebration(finalName: String, animateEntrance: Boolean) {
        stopDeviceFloatAnimation()
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        binding.tvConnectedSubtitle.text = "You have connected to $finalName"
        binding.ivConnectedDeviceIcon.setImageResource(deviceIconRes)

        if (!animateEntrance) {
            binding.layoutConnectingState.visibility = View.GONE
            binding.layoutConnectedState.visibility = View.VISIBLE
            return
        }

        binding.layoutConnectedState.alpha = 0f
        binding.layoutConnectedState.visibility = View.VISIBLE
        binding.layoutConnectingState.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                binding.layoutConnectingState.visibility = View.GONE
                binding.layoutConnectingState.alpha = 1f
            }
            .start()

        binding.layoutConnectedState.animate()
            .alpha(1f)
            .setDuration(300L)
            .start()

        binding.ivConnectedCheck.scaleX = 0.3f
        binding.ivConnectedCheck.scaleY = 0.3f
        binding.ivConnectedCheck.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(460L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        binding.ivConnectedDeviceIcon.scaleX = 0.85f
        binding.ivConnectedDeviceIcon.scaleY = 0.85f
        binding.ivConnectedDeviceIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showWifiInputDialog() {
        if (isFinishing || isDestroyed) return
        val bottomSheet = BottomSheetDialog(this)
        val dialogBinding = DialogPairTuyaDeviceBinding.inflate(layoutInflater)
        bottomSheet.setContentView(dialogBinding.root)
        bottomSheet.setOnShowListener {
            bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialogBinding.ivDialogDeviceIcon.setImageResource(deviceIconRes)
        dialogBinding.tvDialogDeviceName.text = "Hubungkan $deviceName"

        val isCameraDevice = deviceCategory.contains("Kamera", true) ||
            deviceName.contains("Camera", true) ||
            deviceName.contains("Kamera", true) ||
            deviceName.contains("CCTV", true)
        val matchedScanBean = (if (scanUuid.isNotBlank()) TuyaPairingRepository.scannedBeansByUuid[scanUuid] else null)
            ?: TuyaPairingRepository.findMatchingScannedBean(deviceName, deviceCategory)

        if (matchedScanBean != null) {
            dialogBinding.tvDialogSubProtocol.text = "Tuya Smart Life • Bluetooth & Wi-Fi Dual-Mode"
            dialogBinding.tvDialogInstruction.text = "Pilih Wi-Fi 2.4 GHz dan masukkan kata sandi untuk menghubungkan perangkat."
        } else if (isCameraDevice) {
            dialogBinding.tvDialogSubProtocol.text = "Tuya Smart Life • Mode QR Code Lensa"
            dialogBinding.tvDialogInstruction.text = "Masukkan Wi-Fi 2.4 GHz untuk membuat Kode QR bagi lensa kamera."
        } else {
            dialogBinding.tvDialogInstruction.text = "Pastikan perangkat terhubung ke daya dan indikator Wi-Fi berkedip cepat."
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val rawSsid = try { wifiManager?.connectionInfo?.ssid?.replace("\"", "") } catch (_: Exception) { null }
        val finalSsid = when {
            wifiSsid.isNotBlank() -> wifiSsid
            !rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>" -> rawSsid
            else -> "Nadhori Home"
        }
        dialogBinding.etDialogSsid.setText(finalSsid)
        if (wifiPass.isNotBlank()) dialogBinding.etDialogPassword.setText(wifiPass)

        dialogBinding.btnDialogClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        dialogBinding.btnDialogStartPairing.setOnClickListener {
            val ssid = dialogBinding.etDialogSsid.text?.toString()?.trim().orEmpty()
            val pass = dialogBinding.etDialogPassword.text?.toString()?.trim().orEmpty()

            if (ssid.isEmpty()) {
                Toast.makeText(this, "Nama Wi-Fi (SSID) tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            wifiSsid = ssid
            wifiPass = pass
            hideKeyboard(dialogBinding.etDialogPassword)

            // Camera without BLE requires QR scanning in front of lens; all other modes immediately launch the circular ring animation!
            if (isCameraDevice && matchedScanBean == null) {
                dialogBinding.layoutPairingProgress.visibility = View.VISIBLE
                dialogBinding.btnDialogStartPairing.isEnabled = false
                dialogBinding.btnDialogStartPairing.text = "Membuat Kode QR..."
                TuyaDeviceRepository.ensureHomeId(
                    onReady = { validHomeId ->
                        pairingRepo.startCameraQrPairing(
                            context = this,
                            validHomeId = validHomeId,
                            ssid = ssid,
                            pass = pass,
                            timeoutSec = 120,
                            onQrCodeGenerated = { qrBitmap ->
                                runOnUiThread {
                                    dialogBinding.layoutWifiInputs.visibility = View.GONE
                                    dialogBinding.layoutCameraQrCode.visibility = View.VISIBLE
                                    dialogBinding.ivCameraQrCode.setImageBitmap(qrBitmap)
                                }
                            },
                            onDeviceFound = { devResp ->
                                val resolvedName = if (!devResp.name.isNullOrBlank() && !devResp.name.equals("null", true)) devResp.name else deviceName
                                val newDev = TuyaDeviceRepository.addCustomDevice(
                                    name = resolvedName,
                                    category = deviceCategory,
                                    productId = if (!devResp.productId.isNullOrBlank()) devResp.productId else "tt_ipc_v1",
                                    explicitDevId = devResp.devId
                                )
                                runOnUiThread {
                                    bottomSheet.dismiss()
                                    connectedDevice = newDev
                                    isPairingCompleted = true
                                    showConnectedCelebration(resolvedName, animateEntrance = true)
                                }
                            },
                            onError = { code, err ->
                                runOnUiThread {
                                    dialogBinding.layoutCameraQrCode.visibility = View.GONE
                                    dialogBinding.layoutWifiInputs.visibility = View.VISIBLE
                                    dialogBinding.layoutPairingProgress.visibility = View.GONE
                                    dialogBinding.btnDialogStartPairing.isEnabled = true
                                    dialogBinding.btnDialogStartPairing.text = "Coba Lagi"
                                    Toast.makeText(this, "Pairing gagal ($code): $err", Toast.LENGTH_LONG).show()
                                }
                            },
                            onStepUpdate = { step ->
                                runOnUiThread {
                                    dialogBinding.tvCameraQrStatus.text = step
                                    dialogBinding.tvPairingProgressStatus.text = step
                                }
                            }
                        )
                    },
                    onError = { code, err ->
                        runOnUiThread {
                            dialogBinding.layoutPairingProgress.visibility = View.GONE
                            dialogBinding.btnDialogStartPairing.isEnabled = true
                            Toast.makeText(this, "Gagal memuat rumah ($code): $err", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } else {
                bottomSheet.dismiss()
                beginLiveTuyaPairing()
            }
        }

        bottomSheet.show()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun handleExitAttempt() {
        if (isPairingCompleted) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        if (!isPairingInProgress) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Batalkan Penambahan Perangkat?")
            .setMessage("Proses koneksi perangkat ke jaringan Wi-Fi sedang berlangsung.")
            .setPositiveButton("Lanjut Menghubungkan", null)
            .setNegativeButton("Batalkan") { _, _ ->
                pairingRepo.stopPairing()
                stopProgressCrawl()
                finish()
            }
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tips Koneksi Perangkat")
            .setMessage("1. Pastikan jaringan Wi-Fi adalah 2.4 GHz (bukan 5 GHz).\n2. Dekatkan HP dan perangkat dengan router Wi-Fi.\n3. Pastikan kata sandi Wi-Fi sudah benar dan lampu indikator perangkat berkedip cepat.")
            .setPositiveButton("Ubah Wi-Fi") { _, _ ->
                pairingRepo.stopPairing()
                stopProgressCrawl()
                isPairingInProgress = false
                showWifiInputDialog()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showMoreOptionsMenu() {
        val options = arrayOf("Ubah Jaringan Wi-Fi", "Tips & Bantuan Koneksi", "Batalkan Penambahan")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        pairingRepo.stopPairing()
                        stopProgressCrawl()
                        isPairingInProgress = false
                        showWifiInputDialog()
                    }
                    1 -> showHelpDialog()
                    2 -> handleExitAttempt()
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingRepo.stopPairing()
        stopProgressCrawl()
        stopDeviceFloatAnimation()
        handler.removeCallbacksAndMessages(null)
    }
}
