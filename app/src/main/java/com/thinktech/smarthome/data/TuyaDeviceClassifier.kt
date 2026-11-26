package com.thinktech.smarthome.data

import android.content.Context
import android.content.Intent
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thinktech.smarthome.R
import com.thinktech.smarthome.ThinkTechApp
import com.thinktech.smarthome.ui.device.CameraControlActivity
import com.thinktech.smarthome.ui.device.DeviceControlActivity

data class ClassifiedDevice(
    val displayName: String,
    val iconRes: Int,
    val category: String,
    val subcategory: String
)

object TuyaDeviceClassifier {

    fun isCameraDevice(
        devId: String?,
        rawName: String?,
        category: String? = null,
        productId: String? = null
    ): Boolean {
        if (!devId.isNullOrBlank() && !devId.startsWith("demo_") && ThinkTechApp.isSdkInitialized) {
            try {
                if (ThingIPCSdk.getCameraInstance()?.isIPCDevice(devId) == true) {
                    return true
                }
                val sdkCat = ThingHomeSdk.getDataInstance().getDeviceBean(devId)?.category?.lowercase().orEmpty()
                if (sdkCat in setOf("sp", "spz", "videodoorbell", "sp_doorbell")) {
                    return true
                }
            } catch (_: Throwable) {}
        }

        if (productId.equals("tt_ipc_v1", ignoreCase = true)) return true

        val lowerName = rawName?.lowercase().orEmpty()
        val lowerCat = category?.lowercase().orEmpty()

        val isLock = lowerName.contains("lock") || lowerName.contains("kunci") ||
            lowerName.contains("gembok") || lowerName.contains("padlock") || lowerName.contains("brankas")
        if (isLock) return false

        if (lowerCat in setOf("sp", "spz", "videodoorbell", "sp_doorbell")) return true
        if (lowerCat.contains("kamera") || lowerCat.contains("camera")) return true

        return lowerName.contains("camera") ||
            lowerName.contains("kamera") ||
            lowerName.contains("cctv") ||
            lowerName.contains("doorbell") ||
            lowerName.contains("baby monitor") ||
            lowerName == "ty" ||
            lowerName.startsWith("ty_")
    }

    fun resolveChannelCount(
        devId: String?,
        rawName: String?,
        category: String? = null
    ): Int {
        val lowerName = rawName?.lowercase().orEmpty()
        val lowerCat = category?.lowercase().orEmpty()

        if (!devId.isNullOrBlank() && !devId.startsWith("demo_") && ThinkTechApp.isSdkInitialized) {
            try {
                val devBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId)
                val sdkCat = devBean?.category?.lowercase().orEmpty()
                if (sdkCat in setOf("dj", "dd", "xdd", "tynd")) return 1

                val schemaValues = devBean?.schemaMap?.values
                if (!schemaValues.isNullOrEmpty()) {
                    val switchCodes = schemaValues.count { s ->
                        val c = s.code?.lowercase().orEmpty()
                        c.matches(Regex("^switch_?[1-6]$")) || c.matches(Regex("^usb_switch_?[1-6]$"))
                    }
                    if (switchCodes in 1..3) return switchCodes
                    if (switchCodes > 3) return 3
                }
            } catch (_: Throwable) {}
        }

        return when {
            lowerName.contains("3ch") || lowerName.contains("3-gang") || lowerName.contains("3 gang") -> 3
            lowerName.contains("2ch") || lowerName.contains("2-gang") || lowerName.contains("2 gang") -> 2
            lowerName.contains("1ch") || lowerName.contains("1-gang") || lowerName.contains("1 gang") -> 1
            lowerCat.contains("lighting") || lowerCat.contains("lampu") ||
                lowerName.contains("bulb") || lowerName.contains("bohlam") || lowerName.contains("lamp") -> 1
            lowerName.contains("socket") || lowerName.contains("outlet") ||
                lowerName.contains("plug") || lowerName.contains("colokan") || lowerCat.contains("stop kontak") -> 1
            lowerCat.contains("sensor") || lowerCat.contains("keamanan") ||
                lowerName.contains("sensor") || lowerName.contains("lock") || lowerName.contains("kunci") -> 1
            lowerName.contains("switch") || lowerName.contains("saklar") -> 3
            else -> 1
        }
    }

    fun openDeviceControl(
        context: Context,
        devId: String,
        name: String,
        category: String = "",
        productId: String = "",
        isOnline: Boolean = true,
        isSwitchOn: Boolean = false,
        isFromPairing: Boolean = false
    ) {
        val targetClass = if (isCameraDevice(devId, name, category, productId)) {
            CameraControlActivity::class.java
        } else {
            DeviceControlActivity::class.java
        }
        val intent = Intent(context, targetClass).apply {
            putExtra("DEV_ID", devId)
            putExtra("DEV_NAME", name)
            putExtra("DEVICE_CATEGORY", category)
            putExtra("PRODUCT_ID", productId)
            putExtra("IS_ONLINE", isOnline)
            putExtra("IS_ON", isSwitchOn)
            putExtra("IS_FROM_PAIRING", isFromPairing)
        }
        context.startActivity(intent)
    }

    fun classify(
        rawName: String?,
        categoryCode: String? = null,
        productId: String? = null
    ): ClassifiedDevice {
        val name = rawName?.trim().orEmpty()
        val lowerName = name.lowercase()
        val cat = categoryCode?.lowercase().orEmpty()

        // 1. Check Tuya official standard Category Codes (from DeviceBean.category or ProductBean.categoryCode)
        if (cat.isNotEmpty()) {
            when {
                // Kamera & Video
                cat == "sp" || cat == "spz" || cat == "videodoorbell" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Security Camera",
                    iconRes = R.drawable.ic_tuya_camera_indoor,
                    category = "Kamera & Kunci",
                    subcategory = "Security Camera"
                )
                // Kunci Pintu
                cat == "ms" || cat == "jtmms" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Door Lock",
                    iconRes = R.drawable.ic_tuya_lock_door,
                    category = "Kamera & Kunci",
                    subcategory = "Smart Lock"
                )
                // Bel Pintu
                cat == "sp_doorbell" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Video Doorbell",
                    iconRes = R.drawable.ic_tuya_doorbell,
                    category = "Kamera & Kunci",
                    subcategory = "Doorbell"
                )
                // Saklar Listrik (Switch)
                cat == "kg" || cat == "tdq" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "WiFi 3ch switch",
                    iconRes = R.drawable.ic_tuya_wifi_3ch_switch,
                    category = "Electrical",
                    subcategory = "Switch"
                )
                // Stopkontak / Colokan / Plug (Socket)
                cat == "cz" || cat == "pc" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Outlet Listrik",
                    iconRes = R.drawable.ic_tuya_socket,
                    category = "Electrical",
                    subcategory = "Socket"
                )
                // Lampu & Pencahayaan (Lighting)
                cat == "dj" || cat == "dd" || cat == "xdd" || cat == "tynd" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart LED Light",
                    iconRes = R.drawable.ic_tuya_light_bulb,
                    category = "Lighting",
                    subcategory = "Light Source"
                )
                // Sensor Gerak (PIR)
                cat == "pir" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Motion Sensor (PIR)",
                    iconRes = R.drawable.ic_tuya_sensor,
                    category = "Keamanan & Sensor",
                    subcategory = "Sensor"
                )
                // Sensor Pintu / Jendela (Contact Sensor)
                cat == "mc" || cat == "mcs" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Sensor Pintu & Jendela",
                    iconRes = R.drawable.ic_tuya_sensor_door,
                    category = "Keamanan & Sensor",
                    subcategory = "Sensor"
                )
                // Sensor Asap (Smoke)
                cat == "ywbj" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Smoke Alarm",
                    iconRes = R.drawable.ic_tuya_sensor_smoke,
                    category = "Keamanan & Sensor",
                    subcategory = "Sensor"
                )
                // Sirine / Alarm
                cat == "sg" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Siren Alarm",
                    iconRes = R.drawable.ic_tuya_alarm_siren,
                    category = "Keamanan & Sensor",
                    subcategory = "Alarm & Sensor"
                )
                // Suhu & Kelembaban
                cat == "wsdcg" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Sensor Suhu & Kelembaban",
                    iconRes = R.drawable.ic_tuya_sensor,
                    category = "Keamanan & Sensor",
                    subcategory = "Sensor"
                )
                // Robot Vacuum
                cat == "sd" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Robot Vacuum Cleaner",
                    iconRes = R.drawable.ic_tuya_robot_vacuum,
                    category = "Small Home Appliances",
                    subcategory = "Cleaning"
                )
                // AC (Air Conditioner)
                cat == "kt" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart AC",
                    iconRes = R.drawable.ic_tuya_appliance,
                    category = "Large Home Appliances",
                    subcategory = "Climate"
                )
                // Kipas Angin (Fan)
                cat == "fs" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Fan",
                    iconRes = R.drawable.ic_tuya_fan,
                    category = "Small Home Appliances",
                    subcategory = "Fan"
                )
                // Air Purifier
                cat == "kj" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Air Purifier",
                    iconRes = R.drawable.ic_tuya_air_purifier,
                    category = "Small Home Appliances",
                    subcategory = "Air Treatment"
                )
                // Tirai Otomatis (Curtain)
                cat == "cl" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Curtain",
                    iconRes = R.drawable.ic_tuya_curtain,
                    category = "Small Home Appliances",
                    subcategory = "Window Treatment"
                )
                // Gateway / Hub
                cat == "wg2" || cat == "wf_ble" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Gateway Hub",
                    iconRes = R.drawable.ic_tuya_gateway_hub,
                    category = "Gateway Control",
                    subcategory = "Gateway"
                )
                // Pet Feeder
                cat == "cwysj" -> return ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Pet Feeder",
                    iconRes = R.drawable.ic_tuya_pet_feeder,
                    category = "Outdoor Travel",
                    subcategory = "Pet Care"
                )
            }
        }

        // 2. Keyword-based matching from BLE broadcast name
        return when {
            // Camera (Tuya default broadcast is "TY" or starts with "ty_" or keywords)
            lowerName == "ty" || lowerName.startsWith("ty_") || lowerName.startsWith("ty-") ||
                    lowerName.contains("camera") || lowerName.contains("cctv") || lowerName.contains("ipc") ||
                    lowerName.contains("cam") || lowerName.contains("spz") || lowerName.contains("security") -> {
                ClassifiedDevice(
                    displayName = "Security Camera",
                    iconRes = R.drawable.ic_tuya_camera_indoor,
                    category = "Kamera & Kunci",
                    subcategory = "Security Camera"
                )
            }

            // Door Lock
            lowerName.contains("lock") || lowerName.contains("gembok") || lowerName.contains("kunci") || lowerName.contains("padlock") -> {
                ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Door Lock",
                    iconRes = R.drawable.ic_tuya_lock_door,
                    category = "Kamera & Kunci",
                    subcategory = "Smart Lock"
                )
            }

            // Doorbell
            lowerName.contains("doorbell") || lowerName.contains("bell") || lowerName.contains("chime") -> {
                ClassifiedDevice(
                    displayName = if (name.isNotBlank() && !isGenericCode(name)) name else "Smart Video Doorbell",
                    iconRes = R.drawable.ic_tuya_doorbell,
                    category = "Kamera & Kunci",
                    subcategory = "Doorbell"
                )
            }

            // Switch / Saklar (1ch, 2ch, 3ch, 4ch, relay, saklar)
            lowerName.contains("switch") || lowerName.contains("3ch") || lowerName.contains("2ch") ||
                    lowerName.contains("1ch") || lowerName.contains("4ch") || lowerName.contains("relay") ||
                    lowerName.contains("saklar") || lowerName.contains("breaker") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "WiFi 3ch switch" else name,
                    iconRes = if (lowerName.contains("3ch")) R.drawable.ic_tuya_wifi_3ch_switch else R.drawable.ic_tuya_switch,
                    category = "Electrical",
                    subcategory = "Switch"
                )
            }

            // Socket / Plug / Stopkontak / Colokan
            lowerName.contains("socket") || lowerName.contains("plug") || lowerName.contains("outlet") ||
                    lowerName.contains("colokan") || lowerName.contains("stopkontak") || lowerName.contains("strip") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Outlet Listrik" else name,
                    iconRes = if (lowerName.contains("power_strip") || lowerName.contains("strip")) R.drawable.ic_tuya_power_strip else R.drawable.ic_tuya_socket,
                    category = "Electrical",
                    subcategory = "Socket"
                )
            }

            // Lighting (Lampu, Bohlam, Strip, Bulb, Downlight, Ceiling)
            lowerName.contains("light") || lowerName.contains("lamp") || lowerName.contains("bulb") ||
                    lowerName.contains("bohlam") || lowerName.contains("downlight") ||
                    lowerName.contains("ceiling") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Smart LED Light" else name,
                    iconRes = R.drawable.ic_tuya_light_bulb,
                    category = "Lighting",
                    subcategory = "Light Source"
                )
            }

            // Audio & Headphones (KiiP TH100, TWS, Headset, Earphone)
            lowerName.contains("headphone") || lowerName.contains("headset") || lowerName.contains("audio") ||
                    lowerName.contains("th100") || lowerName.contains("kiip") || lowerName.contains("tws") ||
                    lowerName.contains("earphone") || lowerName.contains("earbuds") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || name.length > 15) "KiiP TH100" else name,
                    iconRes = R.drawable.ic_tuya_headphones,
                    category = "Frequently Used",
                    subcategory = "Frequently Used"
                )
            }

            // Siren & Alarm
            lowerName.contains("siren") || lowerName.contains("alarm") || lowerName.contains("sirine") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Smart Siren Alarm" else name,
                    iconRes = R.drawable.ic_tuya_alarm_siren,
                    category = "Keamanan & Sensor",
                    subcategory = "Alarm & Sensor"
                )
            }

            // Sensor (Door, Motion, Smoke, Water, Temp)
            lowerName.contains("sensor") || lowerName.contains("detector") || lowerName.contains("detektor") -> {
                val icon = when {
                    lowerName.contains("door") || lowerName.contains("pintu") -> R.drawable.ic_tuya_sensor_door
                    lowerName.contains("smoke") || lowerName.contains("asap") -> R.drawable.ic_tuya_sensor_smoke
                    lowerName.contains("water") || lowerName.contains("banjir") -> R.drawable.ic_tuya_sensor_water
                    else -> R.drawable.ic_tuya_sensor
                }
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Smart Sensor" else name,
                    iconRes = icon,
                    category = "Keamanan & Sensor",
                    subcategory = "Sensor"
                )
            }

            // Appliances (Vacuum, Purifier, Fan, AC, Kettle, Feeder)
            lowerName.contains("vacuum") || lowerName.contains("sapu") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Robot Vacuum" else name,
                    iconRes = R.drawable.ic_tuya_robot_vacuum,
                    category = "Small Home Appliances",
                    subcategory = "Cleaning"
                )
            }
            lowerName.contains("purifier") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Air Purifier" else name,
                    iconRes = R.drawable.ic_tuya_air_purifier,
                    category = "Small Home Appliances",
                    subcategory = "Air Treatment"
                )
            }
            lowerName.contains("fan") || lowerName.contains("kipas") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Smart Fan" else name,
                    iconRes = R.drawable.ic_tuya_fan,
                    category = "Small Home Appliances",
                    subcategory = "Fan"
                )
            }
            lowerName.contains("feeder") || lowerName.contains("pakan") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Smart Pet Feeder" else name,
                    iconRes = R.drawable.ic_tuya_pet_feeder,
                    category = "Outdoor Travel",
                    subcategory = "Pet Care"
                )
            }

            // Gateway & Hub
            lowerName.contains("gateway") || lowerName.contains("hub") || lowerName.contains("bridge") -> {
                ClassifiedDevice(
                    displayName = if (name.isBlank() || isGenericCode(name)) "Gateway Hub" else name,
                    iconRes = R.drawable.ic_tuya_gateway_hub,
                    category = "Gateway Control",
                    subcategory = "Gateway"
                )
            }

            // Universal Fallback for any other named Tuya device
            else -> {
                val clean = if (name.isBlank() || isGenericCode(name)) "WiFi 3ch switch" else name
                ClassifiedDevice(
                    displayName = clean,
                    iconRes = R.drawable.ic_tuya_wifi_3ch_switch,
                    category = "Electrical",
                    subcategory = "Switch"
                )
            }
        }
    }

    private fun isGenericCode(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower.isBlank() || lower == "null" || lower == "smart" ||
                lower.startsWith("tuya") || lower == "ty" || lower.startsWith("ty_") ||
                lower.startsWith("thing")
    }
}

