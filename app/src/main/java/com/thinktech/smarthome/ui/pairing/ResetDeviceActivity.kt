package com.thinktech.smarthome.ui.pairing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.TuyaPairingRepository
import com.thinktech.smarthome.databinding.ActivityResetDeviceBinding
import com.thinktech.smarthome.databinding.DialogPairTuyaDeviceBinding

class ResetDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetDeviceBinding
    private val pairingRepo = TuyaPairingRepository()

    private val connectingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private var deviceName: String = "Outlet Listrik"
    private var deviceIconRes: Int = R.drawable.ic_tuya_socket
    private var deviceCategory: String = "Electrical"
    private var deviceSubcategory: String = "Socket"
    private var homeId: Long = 0L
    private var currentProtocol: String = "Bluetooth"

    private var currentStep = 1
    private lateinit var currentGuide: TuyaResetGuide

    data class ResetStep(
        val illustrationRes: Int,
        val instruction: String
    )

    data class TuyaResetGuide(
        val step1: ResetStep,
        val step2: ResetStep,
        val step3: ResetStep,
        val wifiInstruction: String
    )

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_ICON = "extra_device_icon"
        const val EXTRA_DEVICE_CATEGORY = "extra_device_category"
        const val EXTRA_DEVICE_SUBCATEGORY = "extra_device_subcategory"
        const val EXTRA_HOME_ID = "extra_home_id"
        const val EXTRA_PROTOCOL = "extra_protocol"

        fun start(
            context: Context,
            name: String,
            iconRes: Int,
            category: String,
            subcategory: String,
            homeId: Long,
            protocol: String = "Bluetooth"
        ): Intent {
            return Intent(context, ResetDeviceActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, name)
                putExtra(EXTRA_DEVICE_ICON, iconRes)
                putExtra(EXTRA_DEVICE_CATEGORY, category)
                putExtra(EXTRA_DEVICE_SUBCATEGORY, subcategory)
                putExtra(EXTRA_HOME_ID, homeId)
                putExtra(EXTRA_PROTOCOL, protocol)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Light status bar with dark icons (matching Tuya UI)
        window.statusBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Outlet Listrik"
        deviceIconRes = intent.getIntExtra(EXTRA_DEVICE_ICON, R.drawable.ic_tuya_socket)
        deviceCategory = intent.getStringExtra(EXTRA_DEVICE_CATEGORY) ?: "Electrical"
        deviceSubcategory = intent.getStringExtra(EXTRA_DEVICE_SUBCATEGORY) ?: "Socket"
        homeId = intent.getLongExtra(EXTRA_HOME_ID, 0L)
        currentProtocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "Bluetooth"

        currentGuide = resolveGuide(deviceCategory, deviceSubcategory, deviceName)

        setupViews()
        updateStepUi(1)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentStep > 1) {
                    currentStep--
                    updateStepUi(currentStep)
                } else {
                    finish()
                }
            }
        })
    }

    private fun resolveGuide(category: String, subcategory: String, name: String): TuyaResetGuide {
        val lowerName = name.lowercase()
        val lowerCat = category.lowercase()
        val lowerSub = subcategory.lowercase()

        // 1. Lighting (Bohlam, Lampu, Strip, Downlight, Ceiling)
        if (lowerCat.contains("lighting") || 
            lowerSub.contains("light") || 
            lowerName.contains("lampu") || 
            lowerName.contains("bohlam") || 
            lowerName.contains("strip") || 
            lowerName.contains("downlight") || 
            lowerName.contains("ceiling") ||
            lowerName.contains("bulb")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_light_step1,
                    instruction = "Turn on the light.\nTurn off after 10s and then turn on again."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_light_step2,
                    instruction = "Turn the switch ON-OFF-ON-OFF-ON\n(Keep 2 to 3 seconds between each operation)."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_light_step3,
                    instruction = "Confirm the light is blinking rapidly"
                ),
                wifiInstruction = "Pastikan lampu pintar terpasang daya dan berkedip cepat."
            )
        }

        // 2. Wall Switch / Saklar (1-Gang, 2-Gang, Touch Switch, Dimmer)
        if (lowerSub.contains("switch") || 
            lowerName.contains("saklar") || 
            lowerName.contains("switch")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_switch_step1,
                    instruction = "Make sure the switch is connected to power."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_switch_step2,
                    instruction = "Press and hold any button on the switch for 5s."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_switch_step3,
                    instruction = "Confirm the indicator is blinking rapidly"
                ),
                wifiInstruction = "Pastikan saklar terhubung ke listrik dan indikator berkedip cepat."
            )
        }

        // 3. Camera & Video Doorbell
        if ((lowerCat.contains("kamera") && !lowerName.contains("kunci")) || 
            lowerName.contains("kamera") || 
            lowerName.contains("camera") || 
            lowerName.contains("doorbell") || 
            lowerName.contains("cctv")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_camera_step1,
                    instruction = "Power on the camera and wait until you hear a prompt tone or the status light flashes."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_camera_step2,
                    instruction = "Press and hold the RESET button for 5s until you hear 'Reset Successfully' or a prompt chime."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_camera_step3,
                    instruction = "Confirm you heard the prompt tone or light is flashing"
                ),
                wifiInstruction = "Pastikan kamera terhubung adaptor daya dan nada konfirmasi terdengar."
            )
        }

        // 4. Smart Door Lock (Kunci Pintu Pintar)
        if (lowerName.contains("kunci") || 
            lowerName.contains("lock") || 
            lowerName.contains("brankas") || 
            lowerName.contains("safe")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_lock_step1,
                    instruction = "Install batteries and wake up the lock keypad."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_lock_step2,
                    instruction = "Press and hold the RESET button behind the battery cover for 5s."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_lock_step3,
                    instruction = "Confirm the voice prompt says 'Please configure network'"
                ),
                wifiInstruction = "Pastikan baterai smart lock terpasang dan keypad menyala."
            )
        }

        // 5. Gateway & Hub (Gateway Control)
        if (lowerCat.contains("gateway") || 
            lowerName.contains("gateway") || 
            lowerName.contains("hub") || 
            lowerName.contains("bridge")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_gateway_step1,
                    instruction = "Connect the gateway to power and router (if wired)."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_gateway_step2,
                    instruction = "Press and hold the reset button for 5s until the indicator changes to flashing."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_gateway_step3,
                    instruction = "Confirm the indicator is blinking"
                ),
                wifiInstruction = "Pastikan gateway terhubung ke daya dan kabel LAN (jika kabel) aktif."
            )
        }

        // 6. Security Sensors (Sensor Pintu, Gerak, Asap, Gas, Banjir, Suhu)
        if (lowerCat.contains("keamanan") || 
            lowerCat.contains("sensor") || 
            lowerName.contains("sensor") || 
            lowerName.contains("detektor")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_sensor_step1,
                    instruction = "Insert the battery or power on the sensor."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_sensor_step2,
                    instruction = "Use a reset pin to hold the RESET hole for 5s (or hold the button until LED flashes)."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_sensor_step3,
                    instruction = "Confirm the status indicator is flashing"
                ),
                wifiInstruction = "Pastikan sensor memiliki baterai cukup dan LED indikator berkedip."
            )
        }

        // 7. Home Appliances (Robot Vacuum, Purifier, AC, Heater, Feeder, etc.)
        if (lowerCat.contains("appliance") || 
            lowerCat.contains("dapur") || 
            lowerName.contains("vacuum") || 
            lowerName.contains("purifier") || 
            lowerName.contains("ac") || 
            lowerName.contains("heater") || 
            lowerName.contains("feeder") || 
            lowerName.contains("dispenser") || 
            lowerName.contains("kulkas") || 
            lowerName.contains("mesin cuci")) {
            return TuyaResetGuide(
                step1 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_appliance_step1,
                    instruction = "Power on the appliance and ensure it is in standby mode."
                ),
                step2 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_appliance_step2,
                    instruction = "Press and hold the Wi-Fi / Power button for 5s until you hear a beep."
                ),
                step3 = ResetStep(
                    illustrationRes = R.drawable.ic_tuya_reset_appliance_step3,
                    instruction = "Confirm the Wi-Fi icon is blinking rapidly"
                ),
                wifiInstruction = "Pastikan perangkat terhubung ke listrik dan ikon Wi-Fi berkedip cepat."
            )
        }

        // 8. Default: Socket / Plug / Power Strip (Electrical)
        return TuyaResetGuide(
            step1 = ResetStep(
                illustrationRes = R.drawable.ic_tuya_reset_step1,
                instruction = "Power on the device.\nPower off after 10s and then power on again."
            ),
            step2 = ResetStep(
                illustrationRes = R.drawable.ic_tuya_reset_step2,
                instruction = "Hold the RESET button for 5s."
            ),
            step3 = ResetStep(
                illustrationRes = R.drawable.ic_tuya_reset_step3,
                instruction = "Confirm the indicator is blinking"
            ),
            wifiInstruction = "Pastikan perangkat terhubung ke daya dan indikator Wi-Fi berkedip cepat."
        )
    }

    private fun setupViews() {
        binding.tvModePill.text = currentProtocol
        binding.tvModePill.setOnClickListener {
            // Toggle between Bluetooth and Wi-Fi
            currentProtocol = if (currentProtocol == "Bluetooth") "Wi-Fi" else "Bluetooth"
            binding.tvModePill.text = currentProtocol
            Toast.makeText(this, "Mode aktivasi: $currentProtocol", Toast.LENGTH_SHORT).show()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        // Single next button on Step 1
        binding.btnSingleNext.setOnClickListener {
            currentStep = 2
            updateStepUi(currentStep)
        }

        // Dual buttons on Step 2 and 3
        binding.btnBack.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepUi(currentStep)
            }
        }

        binding.btnDualNext.setOnClickListener {
            if (currentStep == 2) {
                currentStep = 3
                updateStepUi(currentStep)
            } else if (currentStep == 3) {
                // Step 3 confirmed -> Launch ConnectingDeviceActivity (Gambar 2 verification flow)
                val intent = ConnectingDeviceActivity.start(
                    context = this,
                    name = deviceName,
                    iconRes = deviceIconRes,
                    category = deviceCategory,
                    subcategory = deviceSubcategory,
                    homeId = homeId,
                    connectionType = if (currentProtocol.contains("Bluetooth", true)) "Bluetooth" else "Wi-Fi"
                )
                connectingLauncher.launch(intent)
            }
        }
    }

    private fun updateStepUi(step: Int) {
        when (step) {
            1 -> {
                binding.ivIllustration.setImageResource(currentGuide.step1.illustrationRes)
                
                binding.dotStep1.setBackgroundResource(R.drawable.bg_tuya_step_active)
                binding.tvDot1.setTextColor(Color.WHITE)

                binding.dotStep2.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot2.setTextColor(Color.parseColor("#94A3B8"))

                binding.dotStep3.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot3.setTextColor(Color.parseColor("#94A3B8"))

                binding.tvInstruction.text = currentGuide.step1.instruction

                binding.btnSingleNext.visibility = View.VISIBLE
                binding.layoutDualButtons.visibility = View.GONE
            }
            2 -> {
                binding.ivIllustration.setImageResource(currentGuide.step2.illustrationRes)

                binding.dotStep1.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot1.setTextColor(Color.parseColor("#94A3B8"))

                binding.dotStep2.setBackgroundResource(R.drawable.bg_tuya_step_active)
                binding.tvDot2.setTextColor(Color.WHITE)

                binding.dotStep3.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot3.setTextColor(Color.parseColor("#94A3B8"))

                binding.tvInstruction.text = currentGuide.step2.instruction

                binding.btnSingleNext.visibility = View.GONE
                binding.layoutDualButtons.visibility = View.VISIBLE
            }
            3 -> {
                binding.ivIllustration.setImageResource(currentGuide.step3.illustrationRes)

                binding.dotStep1.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot1.setTextColor(Color.parseColor("#94A3B8"))

                binding.dotStep2.setBackgroundResource(R.drawable.bg_tuya_step_inactive)
                binding.tvDot2.setTextColor(Color.parseColor("#94A3B8"))

                binding.dotStep3.setBackgroundResource(R.drawable.bg_tuya_step_active)
                binding.tvDot3.setTextColor(Color.WHITE)

                binding.tvInstruction.text = currentGuide.step3.instruction

                binding.btnSingleNext.visibility = View.GONE
                binding.layoutDualButtons.visibility = View.VISIBLE
            }
        }
    }

    private fun openWifiPairingSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val dialogBinding = DialogPairTuyaDeviceBinding.inflate(layoutInflater)
        bottomSheet.setContentView(dialogBinding.root)

        dialogBinding.ivDialogDeviceIcon.setImageResource(deviceIconRes)
        val cleanName = deviceName.replace("\n", " ")
        dialogBinding.tvDialogDeviceName.text = "Hubungkan $cleanName"
        dialogBinding.tvDialogInstruction.text = currentGuide.wifiInstruction

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

            dialogBinding.layoutPairingProgress.visibility = View.VISIBLE
            dialogBinding.btnDialogStartPairing.isEnabled = false
            dialogBinding.btnDialogStartPairing.text = "Sedang Mencari..."

            pairingRepo.startEzPairing(
                context = this,
                homeId = homeId,
                ssid = ssid,
                pass = pass,
                timeoutSec = 100,
                onDeviceFound = { devResp ->
                    dialogBinding.layoutPairingProgress.visibility = View.GONE
                    dialogBinding.btnDialogStartPairing.isEnabled = true
                    dialogBinding.btnDialogStartPairing.text = "Mulai Hubungkan"
                    bottomSheet.dismiss()
                    Toast.makeText(this, "Perangkat '${devResp.name}' berhasil terhubung!", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { code, error ->
                    dialogBinding.layoutPairingProgress.visibility = View.GONE
                    dialogBinding.btnDialogStartPairing.isEnabled = true
                    dialogBinding.btnDialogStartPairing.text = "Coba Hubungkan Lagi"
                    Toast.makeText(this, "Pairing gagal ($code): $error", Toast.LENGTH_SHORT).show()
                },
                onStepUpdate = { step ->
                    dialogBinding.tvPairingProgressStatus.text = step
                }
            )
        }

        bottomSheet.show()
    }
}
