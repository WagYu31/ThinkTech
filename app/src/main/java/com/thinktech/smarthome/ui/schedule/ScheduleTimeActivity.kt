package com.thinktech.smarthome.ui.schedule

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.SmartSchedule
import com.thinktech.smarthome.data.SmartScheduleStore
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.databinding.ActivityScheduleTimeBinding
import java.util.Locale
import java.util.UUID

/**
 * Full-Screen Schedule Time Editor with interactive 24-hour & minute radial analog clock,
 * inline 7-day Repeat picker, target device/channel selector, and ON/OFF action toggle.
 * Shared cleanly between Tab Smart (Otomasi Cloud) and DeviceControlActivity.
 */
class ScheduleTimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleTimeBinding

    private var editingId: String? = null
    private var selectedHour: Int = 21
    private var selectedMinute: Int = 0
    private var repeatDays = BooleanArray(7) { true } // 0=Sun .. 6=Sat
    private var targetDeviceId: String = SmartSchedule.DEVICE_ALL
    private var targetDeviceName: String = "Semua Saklar"
    private var targetChannelIndex: Int = -1
    private var targetChannelCount: Int = 1
    private var turnOnAction: Boolean = true
    private var isClockMinuteMode: Boolean = false

    private var deviceOptions = listOf<DeviceOption>()

    private data class DeviceOption(
        val devId: String,
        val name: String,
        val channelCount: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowChrome()
        loadInitialState()
        setupListeners()
        refreshAllUi()
    }

    private fun setupWindowChrome() {
        val bg = Color.parseColor("#F5F7FA")
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.WHITE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    private fun loadInitialState() {
        // Build device picker options passed from caller (plus "Semua Saklar")
        val ids = intent.getStringArrayListExtra(EXTRA_DEVICE_IDS) ?: arrayListOf()
        val names = intent.getStringArrayListExtra(EXTRA_DEVICE_NAMES) ?: arrayListOf()
        val counts = intent.getIntegerArrayListExtra(EXTRA_DEVICE_CHANNELS) ?: arrayListOf()

        val opts = mutableListOf(DeviceOption(SmartSchedule.DEVICE_ALL, "Semua Saklar", 1))
        for (i in ids.indices) {
            val id = ids[i]
            val name = names.getOrNull(i) ?: "Perangkat"
            val ch = counts.getOrNull(i) ?: TuyaDeviceClassifier.resolveChannelCount(id, name, "")
            if (opts.none { it.devId == id }) {
                opts.add(DeviceOption(id, name, ch))
            }
        }

        val presetDevId = intent.getStringExtra(EXTRA_PRESET_DEV_ID)
        val presetDevName = intent.getStringExtra(EXTRA_PRESET_DEV_NAME)
        val presetChCount = intent.getIntExtra(EXTRA_PRESET_CHANNEL_COUNT, 1)
        if (!presetDevId.isNullOrBlank() && !presetDevName.isNullOrBlank()) {
            targetDeviceId = presetDevId
            targetDeviceName = presetDevName
            targetChannelCount = presetChCount
            if (opts.none { it.devId == presetDevId }) {
                opts.add(DeviceOption(presetDevId, presetDevName, presetChCount))
            }
        }
        deviceOptions = opts

        val presetHour = intent.getIntExtra(EXTRA_PRESET_HOUR, -1)
        val presetMin = intent.getIntExtra(EXTRA_PRESET_MINUTE, -1)
        if (presetHour in 0..23) selectedHour = presetHour
        if (presetMin in 0..59) selectedMinute = presetMin
        if (intent.hasExtra(EXTRA_PRESET_TURN_ON)) {
            turnOnAction = intent.getBooleanExtra(EXTRA_PRESET_TURN_ON, true)
        }

        // If editing an existing schedule, restore every field
        editingId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        val existing = editingId?.let { SmartScheduleStore.findById(this, it) }
        if (existing != null) {
            selectedHour = existing.hour
            selectedMinute = existing.minute
            val mask = SmartScheduleStore.normalizeMask(existing.repeatMask)
            for (i in 0 until 7) {
                repeatDays[i] = mask[i] == '1'
            }
            targetDeviceId = existing.deviceId
            targetDeviceName = existing.deviceName
            targetChannelIndex = existing.channelIndex
            targetChannelCount = deviceOptions.firstOrNull { it.devId == existing.deviceId }?.channelCount
                ?: TuyaDeviceClassifier.resolveChannelCount(existing.deviceId, existing.deviceName, "")
            turnOnAction = existing.turnOn
            binding.btnDeleteSchedule.visibility = View.VISIBLE
            binding.tvScheduleHeaderTitle.text = "Edit Waktu Jadwal"
        }

        binding.radialClockView.setTime(selectedHour, selectedMinute)
        binding.radialClockView.setMode(HarmonyRadialClockView.ClockMode.HOUR_24)
    }

    private fun setupListeners() {
        binding.btnBackSchedule.setOnClickListener { finish() }

        binding.btnDeleteSchedule.setOnClickListener {
            val id = editingId ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Hapus Jadwal?")
                .setMessage("Jadwal ${String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)} akan dihapus permanen.")
                .setPositiveButton("Hapus") { _, _ ->
                    SmartScheduleStore.delete(this, id)
                    Toast.makeText(this, "Jadwal berhasil dihapus", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Repeat expand/collapse toggle
        binding.rowRepeatSelector.setOnClickListener {
            val show = binding.layoutRepeatDays.visibility != View.VISIBLE
            binding.layoutRepeatDays.visibility = if (show) View.VISIBLE else View.GONE
            binding.ivRepeatChevron.animate().rotation(if (show) 270f else 180f).setDuration(160L).start()
        }

        val dayViews = listOf(
            binding.daySun, binding.dayMon, binding.dayTue,
            binding.dayWed, binding.dayThu, binding.dayFri, binding.daySat
        )
        dayViews.forEachIndexed { idx, tv ->
            tv.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                repeatDays[idx] = !repeatDays[idx]
                refreshRepeatUi()
            }
        }

        binding.btnRepeatDaily.setOnClickListener {
            repeatDays.fill(true)
            refreshRepeatUi()
        }
        binding.btnRepeatWorkdays.setOnClickListener {
            for (i in 0 until 7) repeatDays[i] = i in 1..5
            refreshRepeatUi()
        }
        binding.btnRepeatOnce.setOnClickListener {
            repeatDays.fill(false)
            refreshRepeatUi()
        }

        // Digital Hour / Minute switcher
        binding.tvClockHour.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchClockMode(minuteMode = false)
        }
        binding.tvClockMinute.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchClockMode(minuteMode = true)
        }

        // Radial Analog Clock listeners
        binding.radialClockView.onHourChanged = { hour ->
            selectedHour = hour
            refreshTimeHeaderUi()
        }
        binding.radialClockView.onHourCommitted = { hour ->
            selectedHour = hour
            refreshTimeHeaderUi()
            // Smoothly step to minute picker right after hour selection (like 78 -> 79)
            switchClockMode(minuteMode = true)
        }
        binding.radialClockView.onMinuteChanged = { minute ->
            selectedMinute = minute
            refreshTimeHeaderUi()
        }

        // Target Device picker
        binding.rowTargetDevice.setOnClickListener {
            val labels = deviceOptions.map { it.name }.toTypedArray()
            val checkedIdx = deviceOptions.indexOfFirst { it.devId == targetDeviceId }.coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Pilih Perangkat Target")
                .setSingleChoiceItems(labels, checkedIdx) { dialog, which ->
                    val chosen = deviceOptions[which]
                    targetDeviceId = chosen.devId
                    targetDeviceName = chosen.name
                    targetChannelCount = chosen.channelCount
                    if (targetChannelCount <= 1) targetChannelIndex = -1
                    refreshTargetUi()
                    dialog.dismiss()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Multi-gang Channel pills
        val chChips = listOf(
            binding.chipChAll to -1,
            binding.chipCh1 to 0,
            binding.chipCh2 to 1,
            binding.chipCh3 to 2
        )
        chChips.forEach { (tv, chIdx) ->
            tv.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                targetChannelIndex = chIdx
                refreshTargetUi()
            }
        }

        // ON / OFF Action toggle
        binding.btnActionTurnOn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            turnOnAction = true
            refreshActionUi()
        }
        binding.btnActionTurnOff.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            turnOnAction = false
            refreshActionUi()
        }

        // Save Schedule CTA
        binding.btnSaveSchedule.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            saveAndFinish()
        }
    }

    private fun switchClockMode(minuteMode: Boolean) {
        isClockMinuteMode = minuteMode
        binding.radialClockView.setMode(
            if (minuteMode) HarmonyRadialClockView.ClockMode.MINUTE else HarmonyRadialClockView.ClockMode.HOUR_24
        )
        refreshTimeHeaderUi()
    }

    private fun refreshAllUi() {
        refreshTimeHeaderUi()
        refreshRepeatUi()
        refreshTargetUi()
        refreshActionUi()
    }

    private fun refreshTimeHeaderUi() {
        binding.tvClockHour.text = String.format(Locale.US, "%02d", selectedHour)
        binding.tvClockMinute.text = String.format(Locale.US, "%02d", selectedMinute)

        val activeInk = Color.parseColor("#141820")
        val inactiveInk = Color.parseColor("#9AA2B1")
        binding.tvClockHour.setTextColor(if (!isClockMinuteMode) activeInk else inactiveInk)
        binding.tvClockMinute.setTextColor(if (isClockMinuteMode) activeInk else inactiveInk)
    }

    private fun refreshRepeatUi() {
        val mask = buildRepeatMask()
        binding.tvRepeatSummary.text = SmartScheduleStore.formatRepeatMask(mask)

        val dayViews = listOf(
            binding.daySun, binding.dayMon, binding.dayTue,
            binding.dayWed, binding.dayThu, binding.dayFri, binding.daySat
        )
        dayViews.forEachIndexed { idx, tv ->
            val on = repeatDays[idx]
            tv.setBackgroundResource(if (on) R.drawable.bg_smart_day_active else R.drawable.bg_smart_day_inactive)
            tv.setTextColor(if (on) Color.WHITE else Color.parseColor("#4B5563"))
        }

        val blue = Color.parseColor("#3B5BFF")
        val ink = Color.parseColor("#2A303D")
        binding.btnRepeatDaily.setTextColor(if (mask == "1111111") blue else ink)
        binding.btnRepeatWorkdays.setTextColor(if (mask == "0111110") blue else ink)
        binding.btnRepeatOnce.setTextColor(if (mask == "0000000") blue else ink)
    }

    private fun refreshTargetUi() {
        binding.tvTargetDeviceName.text = targetDeviceName
        val showChannels = targetDeviceId != SmartSchedule.DEVICE_ALL && targetChannelCount > 1
        binding.layoutChannelRow.visibility = if (showChannels) View.VISIBLE else View.GONE
        if (showChannels) {
            val savedNames = TuyaDeviceRepository.channelNames[targetDeviceId]
            binding.chipCh1.text = savedNames?.getOrNull(0)?.take(8) ?: "L1"
            binding.chipCh2.text = savedNames?.getOrNull(1)?.take(8) ?: "L2"
            binding.chipCh3.text = savedNames?.getOrNull(2)?.take(8) ?: "L3"
            binding.chipCh3.visibility = if (targetChannelCount >= 3) View.VISIBLE else View.GONE

            val chips = listOf(
                binding.chipChAll to -1,
                binding.chipCh1 to 0,
                binding.chipCh2 to 1,
                binding.chipCh3 to 2
            )
            chips.forEach { (tv, idx) ->
                val active = targetChannelIndex == idx
                tv.setBackgroundResource(if (active) R.drawable.bg_lamp_tab_active else R.drawable.bg_lamp_tab_track)
                tv.setTextColor(if (active) Color.WHITE else Color.parseColor("#2A303D"))
            }
        }
    }

    private fun refreshActionUi() {
        val activeBg = R.drawable.bg_lamp_tab_active
        val ink = Color.parseColor("#2A303D")
        if (turnOnAction) {
            binding.btnActionTurnOn.setBackgroundResource(activeBg)
            binding.btnActionTurnOn.setTextColor(Color.WHITE)
            binding.btnActionTurnOff.background = null
            binding.btnActionTurnOff.setTextColor(ink)
        } else {
            binding.btnActionTurnOff.setBackgroundResource(activeBg)
            binding.btnActionTurnOff.setTextColor(Color.WHITE)
            binding.btnActionTurnOn.background = null
            binding.btnActionTurnOn.setTextColor(ink)
        }
    }

    private fun buildRepeatMask(): String =
        repeatDays.joinToString(separator = "") { if (it) "1" else "0" }

    private fun saveAndFinish() {
        val verb = if (turnOnAction) "Nyalakan" else "Matikan"
        val chSuffix = if (targetChannelIndex in 0..2) " L${targetChannelIndex + 1}" else ""
        val autoTitle = "$verb $targetDeviceName$chSuffix"

        val schedule = SmartSchedule(
            id = editingId ?: UUID.randomUUID().toString(),
            title = autoTitle,
            hour = selectedHour,
            minute = selectedMinute,
            repeatMask = buildRepeatMask(),
            deviceId = targetDeviceId,
            deviceName = targetDeviceName,
            channelIndex = if (targetChannelCount > 1 && targetDeviceId != SmartSchedule.DEVICE_ALL) targetChannelIndex else -1,
            turnOn = turnOnAction,
            enabled = true
        )

        SmartScheduleStore.upsert(this, schedule)
        Toast.makeText(
            this,
            "Jadwal ${schedule.formattedTime()} (${schedule.repeatSummary()}) disimpan",
            Toast.LENGTH_SHORT
        ).show()
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "EXTRA_SCHEDULE_ID"
        const val EXTRA_PRESET_DEV_ID = "EXTRA_PRESET_DEV_ID"
        const val EXTRA_PRESET_DEV_NAME = "EXTRA_PRESET_DEV_NAME"
        const val EXTRA_PRESET_CHANNEL_COUNT = "EXTRA_PRESET_CHANNEL_COUNT"
        const val EXTRA_PRESET_HOUR = "EXTRA_PRESET_HOUR"
        const val EXTRA_PRESET_MINUTE = "EXTRA_PRESET_MINUTE"
        const val EXTRA_PRESET_TURN_ON = "EXTRA_PRESET_TURN_ON"
        const val EXTRA_DEVICE_IDS = "EXTRA_DEVICE_IDS"
        const val EXTRA_DEVICE_NAMES = "EXTRA_DEVICE_NAMES"
        const val EXTRA_DEVICE_CHANNELS = "EXTRA_DEVICE_CHANNELS"

        fun createIntent(
            context: Context,
            scheduleId: String? = null,
            presetDevId: String? = null,
            presetDevName: String? = null,
            presetChannelCount: Int = 1,
            availableDevices: List<Triple<String, String, Int>> = emptyList(),
            presetHour: Int? = null,
            presetMinute: Int? = null,
            presetTurnOn: Boolean? = null
        ): Intent {
            return Intent(context, ScheduleTimeActivity::class.java).apply {
                if (scheduleId != null) putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                if (presetDevId != null) putExtra(EXTRA_PRESET_DEV_ID, presetDevId)
                if (presetDevName != null) putExtra(EXTRA_PRESET_DEV_NAME, presetDevName)
                putExtra(EXTRA_PRESET_CHANNEL_COUNT, presetChannelCount)
                if (presetHour != null) putExtra(EXTRA_PRESET_HOUR, presetHour)
                if (presetMinute != null) putExtra(EXTRA_PRESET_MINUTE, presetMinute)
                if (presetTurnOn != null) putExtra(EXTRA_PRESET_TURN_ON, presetTurnOn)
                if (availableDevices.isNotEmpty()) {
                    putStringArrayListExtra(EXTRA_DEVICE_IDS, ArrayList(availableDevices.map { it.first }))
                    putStringArrayListExtra(EXTRA_DEVICE_NAMES, ArrayList(availableDevices.map { it.second }))
                    putIntegerArrayListExtra(EXTRA_DEVICE_CHANNELS, ArrayList(availableDevices.map { it.third }))
                }
            }
        }
    }
}
