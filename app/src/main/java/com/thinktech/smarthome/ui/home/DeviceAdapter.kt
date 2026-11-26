package com.thinktech.smarthome.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thinktech.smarthome.R
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.model.SmartDeviceItem
import com.thinktech.smarthome.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onDeviceClicked: (SmartDeviceItem) -> Unit,
    private val onTogglePower: (device: SmartDeviceItem, isChecked: Boolean) -> Unit,
    private val onDeviceLongClicked: ((SmartDeviceItem) -> Unit)? = null
) : ListAdapter<SmartDeviceItem, DeviceAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SmartDeviceItem) {
            val ctx = binding.root.context
            val density = ctx.resources.displayMetrics.density

            binding.tvDeviceName.text = item.name

            val sdkCat = if (ThinkTechApp.isSdkInitialized && item.devId.isNotBlank() && !item.devId.startsWith("demo_")) {
                try { ThingHomeSdk.getDataInstance().getDeviceBean(item.devId)?.category } catch (_: Throwable) { null }
            } else {
                null
            }
            val classified = TuyaDeviceClassifier.classify(
                rawName = item.name,
                categoryCode = sdkCat,
                productId = item.productId
            )
            val displayCategory = item.category.ifEmpty { classified.category }.ifEmpty { "Perangkat IoT" }
            val roomPrefix = item.room.ifBlank { "Rumah" }
            binding.tvCategory.text = "$roomPrefix • $displayCategory"

            binding.ivDeviceIcon.imageTintList = null
            binding.ivDeviceIcon.setImageResource(classified.iconRes)

            val isCamera = TuyaDeviceClassifier.isCameraDevice(
                devId = item.devId,
                rawName = item.name,
                category = displayCategory,
                productId = item.productId
            )
            val isDemo = item.devId.startsWith("demo_")

            // Commercial Huawei AI Life restraint: keep real physical devices 100% badge-free;
            // show subtle Demo tag only on local simulation items.
            if (isDemo) {
                binding.tvDeviceBadge.visibility = View.VISIBLE
                binding.tvDeviceBadge.text = "Demo"
                binding.tvDeviceBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.hyper_amber_soft))
                binding.tvDeviceBadge.setTextColor(ContextCompat.getColor(ctx, R.color.hyper_amber_text))
            } else {
                binding.tvDeviceBadge.visibility = View.GONE
            }

            val isActiveVisual = item.isOnline && (isCamera || item.isSwitchOn)

            // Huawei HarmonyOS Light-Field Optical Card State
            binding.cardDevice.setCornerRadiusDp(24f)
            binding.cardDevice.lightFieldMode = if (isActiveVisual) {
                HarmonyLightFieldCard.Mode.DEVICE_ON
            } else {
                HarmonyLightFieldCard.Mode.DEVICE_OFF
            }
            binding.cardDevice.elevation = if (isActiveVisual) 3.5f * density else 1f * density
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                binding.cardDevice.outlineSpotShadowColor = if (isActiveVisual) 0x260A59F7 else 0x120A192F
                binding.cardDevice.outlineAmbientShadowColor = 0x0C0A192F
            }
            binding.cardDevice.alpha = if (item.isOnline) 1.0f else 0.64f

            // Circular Status Dot (oval shape preserved via backgroundTintList)
            when {
                !item.isOnline -> {
                    binding.dotStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.device_offline))
                    binding.tvStatus.text = "Offline"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.device_offline))
                }
                isCamera -> {
                    binding.dotStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.hyper_emerald))
                    binding.tvStatus.text = "Siap Live"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.hyper_emerald_text))
                }
                item.isSwitchOn -> {
                    binding.dotStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.harmony_blue))
                    binding.tvStatus.text = "Menyala"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.harmony_blue))
                }
                else -> {
                    binding.dotStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.hyper_emerald))
                    binding.tvStatus.text = "Mati"
                    binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.harmony_ink_secondary))
                }
            }

            // Action Button: Camera LIVE Pill vs HarmonyOS Circular Power Toggle
            if (isCamera) {
                binding.btnPowerCircle.visibility = View.GONE
                binding.tvCameraLivePill.visibility = View.VISIBLE
                binding.tvCameraLivePill.alpha = if (item.isOnline) 1.0f else 0.55f
                binding.tvCameraLivePill.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDeviceClicked(item)
                }
            } else {
                binding.tvCameraLivePill.visibility = View.GONE
                binding.btnPowerCircle.visibility = View.VISIBLE
                binding.btnPowerCircle.isEnabled = item.isOnline
                binding.btnPowerCircle.alpha = if (item.isOnline) 1.0f else 0.45f

                if (item.isOnline && item.isSwitchOn) {
                    binding.btnPowerCircle.setBackgroundResource(R.drawable.bg_harmony_power_on)
                    binding.ivPowerIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                } else {
                    binding.btnPowerCircle.setBackgroundResource(R.drawable.bg_harmony_power_off)
                    binding.ivPowerIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.harmony_ink))
                }

                binding.btnPowerCircle.setOnClickListener { btn ->
                    if (!item.isOnline) return@setOnClickListener
                    btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    btn.animate()
                        .scaleX(0.84f)
                        .scaleY(0.84f)
                        .setDuration(75L)
                        .withEndAction {
                            btn.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(160L)
                                .setInterpolator(OvershootInterpolator(2.1f))
                                .start()
                        }
                        .start()
                    onTogglePower(item, !item.isSwitchOn)
                }
            }

            binding.cardDevice.setOnClickListener {
                onDeviceClicked(item)
            }

            binding.cardDevice.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onDeviceLongClicked?.invoke(item)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SmartDeviceItem>() {
        override fun areItemsTheSame(oldItem: SmartDeviceItem, newItem: SmartDeviceItem): Boolean {
            return oldItem.devId == newItem.devId
        }

        override fun areContentsTheSame(oldItem: SmartDeviceItem, newItem: SmartDeviceItem): Boolean {
            return oldItem == newItem
        }
    }
}
