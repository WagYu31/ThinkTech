package com.thinktech.smarthome.ui.pairing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thinktech.smarthome.R
import com.thinktech.smarthome.data.TuyaDeviceClassifier
import com.thinktech.smarthome.data.TuyaDeviceRepository
import com.thinktech.smarthome.data.TuyaPairingRepository
import android.net.wifi.WifiManager
import com.thingclips.smart.android.ble.api.BleConfigType
import com.thingclips.smart.android.ble.api.BleScanResponse
import com.thingclips.smart.android.ble.api.LeScanSetting
import com.thingclips.smart.android.ble.api.ScanDeviceBean
import com.thingclips.smart.android.ble.api.ScanType
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thinktech.smarthome.databinding.ActivityPairDeviceBinding
import com.thinktech.smarthome.databinding.DialogPairTuyaDeviceBinding
import com.thinktech.smarthome.databinding.ItemOrbitDiscoveredDeviceBinding
import com.thinktech.smarthome.databinding.ItemTuyaCategoryTabBinding
import com.thinktech.smarthome.databinding.ItemTuyaDeviceBinding
import com.thinktech.smarthome.databinding.ItemTuyaSearchResultBinding
import com.thinktech.smarthome.ui.home.MainActivity
import com.thinktech.smarthome.ui.device.DeviceControlActivity
import kotlin.math.cos
import kotlin.math.sin

class PairDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairDeviceBinding
    private val pairingRepo = TuyaPairingRepository()
    private val deviceRepo = TuyaDeviceRepository()
    private var homeId: Long = 0

    data class DiscoveredDevice(
        val idKey: String,
        val name: String,
        val iconRes: Int,
        val category: String,
        val subcategory: String,
        val protocol: String = "Bluetooth & Wi-Fi",
        val scanBean: ScanDeviceBean? = null
    )

    private val discoveredDevices = mutableListOf<DiscoveredDevice>()
    private val cachedScanBeans = mutableMapOf<String, ScanDeviceBean>()
    private val renderedOrbitMap = mutableMapOf<String, ItemOrbitDiscoveredDeviceBinding>()
    private var isNearbyTabSelected = true
    private val scanHandler = Handler(Looper.getMainLooper())
    private var discoveryRunnable: Runnable? = null
    private var autoAddTimer: Runnable? = null
    private var remainingSeconds = 18
    private var isPairingDialogOpen = false
    private var isAutoAddCancelled = false
    private var currentPairingBottomSheet: BottomSheetDialog? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanCallback: ScanCallback? = null
    private var isTuyaScanning = false

    private val resetDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private val connectingDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private val categories = listOf(
        "Frequently Used",
        "Electrical",
        "Lighting",
        "Keamanan & Sensor",
        "Large Home Appliances",
        "Small Home Appliances",
        "Dapur",
        "Exercise & Health",
        "Kamera & Kunci",
        "Gateway Control",
        "Outdoor Travel",
        "Energy",
        "Entertainment",
        "Industry & Agriculture",
        "Lainnya"
    )

    data class TuyaDevice(
        val name: String,
        val iconRes: Int,
        val category: String,
        val subcategory: String,
        val searchKeywords: List<String> = emptyList()
    )

    private val allDeviceCatalog = listOf(
        // ========================================================
        // 0. FREQUENTLY USED (Flagship Curated Smart Home Essentials)
        // ========================================================
        TuyaDevice("WiFi 3ch switch", R.drawable.ic_tuya_wifi_3ch_switch, "Frequently Used", "Populer", listOf("wifi", "3ch", "switch", "saklar")),
        TuyaDevice("Security Camera\nPTZ 360°", R.drawable.ic_tuya_camera_ptz, "Frequently Used", "Populer", listOf("camera", "cctv", "ptz", "kamera")),
        TuyaDevice("Smart Bulb\nRGB+CCT", R.drawable.ic_tuya_bulb_rgb, "Frequently Used", "Populer", listOf("lamp", "bulb", "rgb", "bohlam")),
        TuyaDevice("Smart Plug 16A\n(Power Meter)", R.drawable.ic_tuya_plug, "Frequently Used", "Listrik & Soket", listOf("plug", "socket", "colokan", "meter")),
        TuyaDevice("Outlet Listrik", R.drawable.ic_tuya_socket, "Frequently Used", "Listrik & Soket", listOf("socket", "colokan", "outlet")),
        TuyaDevice("Outlet Listrik\n(Gateway)", R.drawable.ic_tuya_socket, "Frequently Used", "Listrik & Soket", listOf("socket", "gateway")),
        TuyaDevice("Smart Door Lock\n(Wi-Fi)", R.drawable.ic_tuya_lock_door, "Frequently Used", "Keamanan & Rumah", listOf("lock", "kunci", "fingerprint")),
        TuyaDevice("Motion Sensor\nPIR", R.drawable.ic_tuya_sensor, "Frequently Used", "Keamanan & Rumah", listOf("sensor", "pir", "gerak")),
        TuyaDevice("Motor Tirai\nPintar", R.drawable.ic_tuya_curtain, "Frequently Used", "Keamanan & Rumah", listOf("curtain", "tirai", "gorden")),
        TuyaDevice("Air Conditioner\nPintar", R.drawable.ic_tuya_appliance, "Frequently Used", "Iklim & Audio", listOf("ac", "air conditioner", "pendingin")),
        TuyaDevice("Kipas Angin\nPintar", R.drawable.ic_tuya_fan, "Frequently Used", "Iklim & Audio", listOf("fan", "kipas")),
        TuyaDevice("KiiP TH100", R.drawable.ic_tuya_headphones, "Frequently Used", "Iklim & Audio", listOf("kiip", "th100", "headphone", "audio", "headset")),

        // ========================================================
        // 1. ELECTRICAL (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Socket
        TuyaDevice("Outlet Listrik", R.drawable.ic_tuya_socket, "Electrical", "Socket", listOf("socket", "colokan", "stopkontak", "outlet", "plug")),
        TuyaDevice("Outlet Listrik\n(Gateway)", R.drawable.ic_tuya_socket, "Electrical", "Socket", listOf("socket", "gateway", "zigbee", "colokan")),
        TuyaDevice("Outlet Listrik\n(NB-IoT)", R.drawable.ic_tuya_socket, "Electrical", "Socket", listOf("socket", "iot", "nb-iot")),
        TuyaDevice("Outlet Listrik\n(BLE)", R.drawable.ic_tuya_socket, "Electrical", "Socket", listOf("socket", "ble", "bluetooth")),
        TuyaDevice("Outlet Dinding\nPintar", R.drawable.ic_tuya_socket, "Electrical", "Socket", listOf("socket", "dinding", "wall socket")),
        TuyaDevice("Smart Plug 16A\n(Power Meter)", R.drawable.ic_tuya_plug, "Electrical", "Socket", listOf("plug", "16a", "meter", "daya", "colokan")),
        TuyaDevice("Outlet Luar\nRuang (IP66)", R.drawable.ic_tuya_socket_outdoor, "Electrical", "Socket", listOf("outdoor", "waterproof", "anti air", "taman")),
        TuyaDevice("Soket Multi-USB\nPintar", R.drawable.ic_tuya_socket_usb, "Electrical", "Socket", listOf("usb", "soket", "charger")),

        // Power Strip
        TuyaDevice("Pasang", R.drawable.ic_tuya_power_strip, "Electrical", "Power Strip", listOf("strip", "colokan", "pasang", "power strip")),
        TuyaDevice("Pasang\n(Gateway)", R.drawable.ic_tuya_power_strip, "Electrical", "Power Strip", listOf("strip", "gateway", "zigbee")),
        TuyaDevice("Pasang\n(BLE)", R.drawable.ic_tuya_power_strip, "Electrical", "Power Strip", listOf("strip", "ble", "bluetooth")),
        TuyaDevice("Smart Extension\nLead 4-Port", R.drawable.ic_tuya_power_strip, "Electrical", "Power Strip", listOf("extension", "lead", "terminal", "colokan")),
        TuyaDevice("Surge Protector\nPintar", R.drawable.ic_tuya_power_strip, "Electrical", "Power Strip", listOf("surge", "protector", "anti petir")),

        // Switch
        TuyaDevice("WiFi 3ch switch", R.drawable.ic_tuya_wifi_3ch_switch, "Electrical", "Switch", listOf("wifi", "3ch", "switch", "saklar", "3 gang")),
        TuyaDevice("Saklar Dinding", R.drawable.ic_tuya_switch, "Electrical", "Switch", listOf("switch", "saklar", "dinding")),
        TuyaDevice("Saklar Dinding\n(Gateway)", R.drawable.ic_tuya_switch, "Electrical", "Switch", listOf("switch", "gateway", "zigbee")),
        TuyaDevice("Saklar Dinding\n(BLE)", R.drawable.ic_tuya_switch, "Electrical", "Switch", listOf("switch", "ble", "bluetooth")),
        TuyaDevice("Saklar Dinding\n(1-Gang)", R.drawable.ic_tuya_switch_1gang, "Electrical", "Switch", listOf("switch", "1 gang", "saklar 1")),
        TuyaDevice("Saklar Dinding\n(2-Gang)", R.drawable.ic_tuya_switch_2gang, "Electrical", "Switch", listOf("switch", "2 gang", "saklar 2")),
        TuyaDevice("Saklar Dinding\n(3-Gang)", R.drawable.ic_tuya_switch_3gang, "Electrical", "Switch", listOf("switch", "3 gang", "saklar 3")),
        TuyaDevice("Saklar Dinding\n(4-Gang)", R.drawable.ic_tuya_switch_4gang, "Electrical", "Switch", listOf("switch", "4 gang", "saklar 4")),
        TuyaDevice("Saklar Sentuh\nTouch Switch", R.drawable.ic_tuya_switch_touch, "Electrical", "Switch", listOf("touch", "sentuh", "kaca")),
        TuyaDevice("Saklar Tanpa\nNetral (No-N)", R.drawable.ic_tuya_switch_1gang, "Electrical", "Switch", listOf("no neutral", "tanpa netral", "single fire")),
        TuyaDevice("Saklar Dinding\nMatter", R.drawable.ic_tuya_switch_2gang, "Electrical", "Switch", listOf("matter", "thread", "apple home")),

        // Relay Module
        TuyaDevice("WiFi 3ch switch", R.drawable.ic_tuya_wifi_3ch_switch, "Electrical", "Relay Module", listOf("wifi", "3ch", "switch", "saklar", "3 gang")),
        TuyaDevice("Modul Saklar Mini\n(3-Gang Wi-Fi)", R.drawable.ic_tuya_wifi_3ch_switch, "Electrical", "Relay Module", listOf("relay", "mini", "3 gang", "modul", "wifi 3ch switch")),
        TuyaDevice("Modul Saklar Mini\n(1-Gang)", R.drawable.ic_tuya_relay, "Electrical", "Relay Module", listOf("relay", "mini", "in-wall", "modul")),
        TuyaDevice("Modul Saklar Mini\n(2-Gang)", R.drawable.ic_tuya_relay, "Electrical", "Relay Module", listOf("relay", "mini", "2 gang", "modul")),
        TuyaDevice("Modul Kontak\nKering (Dry)", R.drawable.ic_tuya_relay, "Electrical", "Relay Module", listOf("dry contact", "gerbang", "kontak kering")),
        TuyaDevice("Saklar DIN Rail\nPintar", R.drawable.ic_tuya_breaker, "Electrical", "Relay Module", listOf("din rail", "panel", "relay")),

        // Dimmer Switch
        TuyaDevice("Dimmer Switch", R.drawable.ic_tuya_dimmer, "Electrical", "Dimmer Switch", listOf("dimmer", "redup", "terang")),
        TuyaDevice("Dimmer Switch\n(Gateway)", R.drawable.ic_tuya_dimmer, "Electrical", "Dimmer Switch", listOf("dimmer", "gateway", "zigbee")),
        TuyaDevice("Dimmer Rotary\nKnob", R.drawable.ic_tuya_dimmer, "Electrical", "Dimmer Switch", listOf("rotary", "knob", "putar")),
        TuyaDevice("Modul In-Wall\nDimmer", R.drawable.ic_tuya_dimmer, "Electrical", "Dimmer Switch", listOf("in wall", "dimmer module")),

        // Curtain Switch & Motor
        TuyaDevice("Tirai beralih", R.drawable.ic_tuya_curtain_switch, "Electrical", "Curtain Switch", listOf("curtain", "tirai", "gorden")),
        TuyaDevice("Tirai beralih\n(Gateway)", R.drawable.ic_tuya_curtain_switch, "Electrical", "Curtain Switch", listOf("curtain", "gateway", "zigbee")),
        TuyaDevice("Motor Tirai\nPintar", R.drawable.ic_tuya_curtain, "Electrical", "Curtain Switch", listOf("curtain", "motor", "track")),
        TuyaDevice("Motor Roller\nBlind Pintar", R.drawable.ic_tuya_curtain, "Electrical", "Curtain Switch", listOf("roller", "blind", "gulung")),
        TuyaDevice("Motor Tubular\nPintar", R.drawable.ic_tuya_curtain, "Electrical", "Curtain Switch", listOf("tubular", "shutter", "kanopi")),
        TuyaDevice("Robot Penarik\nTirai Pintar", R.drawable.ic_tuya_curtain, "Electrical", "Curtain Switch", listOf("robot", "tirai", "curtain robot")),

        // Fan Switch
        TuyaDevice("Fan Switch", R.drawable.ic_tuya_fan, "Electrical", "Fan Switch", listOf("fan", "kipas", "saklar kipas")),
        TuyaDevice("Fan Switch\n(Gateway)", R.drawable.ic_tuya_fan, "Electrical", "Fan Switch", listOf("fan", "gateway", "zigbee")),
        TuyaDevice("Fan Light Switch\nPintar", R.drawable.ic_tuya_fan, "Electrical", "Fan Switch", listOf("fan light", "lampu kipas")),

        // Scenario Switch
        TuyaDevice("Skenario beralih", R.drawable.ic_tuya_scenario, "Electrical", "Scenario Switch", listOf("scenario", "scene", "skenario")),
        TuyaDevice("Skenario beralih\n(Gateway)", R.drawable.ic_tuya_scenario, "Electrical", "Scenario Switch", listOf("scenario", "gateway", "zigbee")),
        TuyaDevice("Remote Skenario\n4-Tombol", R.drawable.ic_tuya_scenario, "Electrical", "Scenario Switch", listOf("remote", "scene 4", "tombol")),

        // Wireless Switch
        TuyaDevice("Sakelar nirkabel\n(Gateway)", R.drawable.ic_tuya_switch_1gang, "Electrical", "Wireless Switch", listOf("wireless", "switch", "nirkabel", "zigbee")),
        TuyaDevice("Sakelar nirkabel\n(BLE)", R.drawable.ic_tuya_switch_1gang, "Electrical", "Wireless Switch", listOf("wireless", "switch", "ble")),
        TuyaDevice("Tombol Pintar\nKinetik", R.drawable.ic_tuya_switch_1gang, "Electrical", "Wireless Switch", listOf("kinetic", "tanpa baterai")),

        // Card Switch
        TuyaDevice("Card Switch\n(Gateway)", R.drawable.ic_tuya_card_switch, "Electrical", "Card Switch", listOf("card", "hotel", "kartu", "gateway")),
        TuyaDevice("Card Switch", R.drawable.ic_tuya_card_switch, "Electrical", "Card Switch", listOf("card", "hotel", "kartu", "wifi")),
        TuyaDevice("Saklar Hemat\nEnergi Hotel", R.drawable.ic_tuya_card_switch, "Electrical", "Card Switch", listOf("hotel", "energy saving")),

        // Breaker
        TuyaDevice("Pemutus Sirkuit\n(MCB Wi-Fi)", R.drawable.ic_tuya_breaker, "Electrical", "Breaker", listOf("breaker", "mcb", "pemutus")),
        TuyaDevice("Pemutus Sirkuit\n(Gateway)", R.drawable.ic_tuya_breaker, "Electrical", "Breaker", listOf("breaker", "gateway", "zigbee")),
        TuyaDevice("Smart RCBO\nLeakage Breaker", R.drawable.ic_tuya_breaker, "Electrical", "Breaker", listOf("rcbo", "elcb", "kebocoran")),

        // ========================================================
        // 2. LIGHTING (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Light Source
        TuyaDevice("Lampu Bohlam\n(Wi-Fi)", R.drawable.ic_tuya_light_bulb, "Lighting", "Light Source", listOf("lamp", "bulb", "bohlam", "wifi")),
        TuyaDevice("Lampu Bohlam\n(Bluetooth)", R.drawable.ic_tuya_light_bulb, "Lighting", "Light Source", listOf("lamp", "bulb", "bohlam", "ble")),
        TuyaDevice("Lampu Bohlam\n(Gateway)", R.drawable.ic_tuya_light_bulb, "Lighting", "Light Source", listOf("lamp", "bulb", "bohlam", "zigbee")),
        TuyaDevice("Lampu Bohlam\nRGB+CCT", R.drawable.ic_tuya_bulb_rgb, "Lighting", "Light Source", listOf("lamp", "rgb", "color", "warna")),
        TuyaDevice("Filament lamp\n(Wi-Fi)", R.drawable.ic_tuya_bulb_filament, "Lighting", "Light Source", listOf("filament", "vintage", "edison", "bohlam")),
        TuyaDevice("Filament lamp\n(Gateway)", R.drawable.ic_tuya_bulb_filament, "Lighting", "Light Source", listOf("filament", "gateway", "zigbee")),
        TuyaDevice("Bohlam Lilin E14\nPintar", R.drawable.ic_tuya_bulb_filament, "Lighting", "Light Source", listOf("candle", "e14", "lilin", "chandelier")),
        TuyaDevice("Spotlight GU10\nPintar", R.drawable.ic_tuya_downlight, "Lighting", "Light Source", listOf("gu10", "spotlight", "sorot")),

        // Strip Lights
        TuyaDevice("Strip Lampu LED\n(Wi-Fi)", R.drawable.ic_tuya_strip_light, "Lighting", "Strip Light", listOf("strip", "led", "lampu pita")),
        TuyaDevice("Strip Lampu LED\n(BLE)", R.drawable.ic_tuya_strip_light, "Lighting", "Strip Light", listOf("strip", "ble", "bluetooth")),
        TuyaDevice("Strip Lampu Neon\nFleksibel", R.drawable.ic_tuya_strip_light, "Lighting", "Strip Light", listOf("neon", "rope", "silikon")),
        TuyaDevice("Strip TV Sync\nAmbient Light", R.drawable.ic_tuya_strip_light, "Lighting", "Strip Light", listOf("tv sync", "backlight", "ambient")),
        TuyaDevice("COB LED Strip\nPintar", R.drawable.ic_tuya_strip_light, "Lighting", "Strip Light", listOf("cob", "strip", "seamless")),

        // Plafon & Downlight
        TuyaDevice("Downlight Pintar\n(Wi-Fi)", R.drawable.ic_tuya_downlight, "Lighting", "Plafon & Downlight", listOf("downlight", "plafon", "ceiling")),
        TuyaDevice("Downlight Pintar\n(Bluetooth)", R.drawable.ic_tuya_downlight, "Lighting", "Plafon & Downlight", listOf("downlight", "ble")),
        TuyaDevice("Downlight Pintar\n(Gateway)", R.drawable.ic_tuya_downlight, "Lighting", "Plafon & Downlight", listOf("downlight", "zigbee")),
        TuyaDevice("Lampu Plafon\nPintar", R.drawable.ic_tuya_ceiling_light, "Lighting", "Plafon & Downlight", listOf("plafon", "ceiling light", "lampu utama")),
        TuyaDevice("Lampu Panel LED\nPintar", R.drawable.ic_tuya_ceiling_light, "Lighting", "Plafon & Downlight", listOf("panel", "kantor", "led panel")),
        TuyaDevice("Smart Magnetic\nTrack Light", R.drawable.ic_tuya_downlight, "Lighting", "Plafon & Downlight", listOf("track light", "magnetic", "rel")),

        // Lampu Meja & Tidur
        TuyaDevice("Table Lamp\n(BLE+Wi-Fi)", R.drawable.ic_tuya_table_lamp, "Lighting", "Lampu Meja & Tidur", listOf("table", "meja", "desk lamp", "baca")),
        TuyaDevice("Lampu Tidur\n(Bedside Lamp)", R.drawable.ic_tuya_bedside_lamp, "Lighting", "Lampu Meja & Tidur", listOf("tidur", "bedside", "night light")),
        TuyaDevice("Lampu Tidur\n(Gateway)", R.drawable.ic_tuya_bedside_lamp, "Lighting", "Lampu Meja & Tidur", listOf("tidur", "gateway", "zigbee")),
        TuyaDevice("Atmosphere lamp\n(Wi-Fi)", R.drawable.ic_tuya_bulb_rgb, "Lighting", "Lampu Meja & Tidur", listOf("atmosphere", "suasana", "mood")),
        TuyaDevice("Lampu Sudut\nLantai RGB", R.drawable.ic_tuya_bulb_rgb, "Lighting", "Lampu Meja & Tidur", listOf("corner", "floor lamp", "sudut")),
        TuyaDevice("Lampu nyamuk", R.drawable.ic_tuya_mosquito_lamp, "Lighting", "Lampu Meja & Tidur", listOf("nyamuk", "mosquito", "perangkap")),

        // Outdoor & Garden
        TuyaDevice("Lampu Sorot Taman\n(Solar Spike)", R.drawable.ic_tuya_downlight, "Lighting", "Outdoor & Taman", listOf("solar", "taman", "spike", "kebun")),
        TuyaDevice("Lampu Dinding\nOutdoor Pintar", R.drawable.ic_tuya_ceiling_light, "Lighting", "Outdoor & Taman", listOf("dinding luar", "wall light", "outdoor")),
        TuyaDevice("Lampu Sorot LED\nFloodlight", R.drawable.ic_tuya_downlight, "Lighting", "Outdoor & Taman", listOf("floodlight", "sorot", "tembak")),
        TuyaDevice("Lampu Kolam\nRenang RGB", R.drawable.ic_tuya_bulb_rgb, "Lighting", "Outdoor & Taman", listOf("kolam", "pool", "underwater")),

        // Soket Lampu & Controller
        TuyaDevice("Soket lampu adegan\n(Wi-Fi)", R.drawable.ic_tuya_scene_socket, "Lighting", "Soket Lampu", listOf("soket", "adegan", "wifi", "fitting")),
        TuyaDevice("Soket lampu adegan\n(Gateway)", R.drawable.ic_tuya_scene_socket, "Lighting", "Soket Lampu", listOf("soket", "gateway", "zigbee")),
        TuyaDevice("Soket lampu adegan\n(other)", R.drawable.ic_tuya_scene_socket, "Lighting", "Soket Lampu", listOf("soket", "other")),
        TuyaDevice("Smart LED Dimmer\nController", R.drawable.ic_tuya_dimmer, "Lighting", "Soket Lampu", listOf("driver", "dimmer controller", "led")),

        // ========================================================
        // 3. KEAMANAN & SENSOR (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Sensor Gerak & Kehadiran
        TuyaDevice("Sensor Gerak PIR\n(Wi-Fi)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Gerak", listOf("pir", "gerak", "motion", "wifi")),
        TuyaDevice("Sensor Gerak PIR\n(Gateway)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Gerak", listOf("pir", "gateway", "zigbee")),
        TuyaDevice("Sensor Presensi\nmmWave Radar", R.drawable.ic_tuya_sensor_radar, "Keamanan & Sensor", "Sensor Gerak", listOf("mmwave", "radar", "presensi", "kehadiran manusia")),
        TuyaDevice("Sensor Gerak\nSolar Outdoor", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Gerak", listOf("solar", "outdoor", "pir luar")),

        // Sensor Kontak
        TuyaDevice("Sensor Pintu\n& Jendela (Wi-Fi)", R.drawable.ic_tuya_sensor_door, "Keamanan & Sensor", "Sensor Kontak", listOf("door", "window", "pintu", "jendela", "magnet")),
        TuyaDevice("Sensor Pintu\n& Jendela (Zigbee)", R.drawable.ic_tuya_sensor_door, "Keamanan & Sensor", "Sensor Kontak", listOf("door", "window", "gateway", "zigbee")),
        TuyaDevice("Sensor Pintu\nGarasi Pintar", R.drawable.ic_tuya_sensor_door, "Keamanan & Sensor", "Sensor Kontak", listOf("garage", "garasi", "pintu garasi")),

        // Sensor Lingkungan
        TuyaDevice("Sensor Suhu\n& Kelembaban", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Lingkungan", listOf("suhu", "kelembaban", "temperature", "humidity")),
        TuyaDevice("Sensor Suhu\n(Zigbee LCD)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Lingkungan", listOf("lcd", "suhu", "zigbee", "display")),
        TuyaDevice("Monitor Kualitas\nUdara (CO2/PM2.5)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Lingkungan", listOf("co2", "pm2.5", "voc", "kualitas udara")),
        TuyaDevice("Sensor Intensitas\nCahaya (Lux)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Sensor Lingkungan", listOf("lux", "cahaya", "luminance")),

        // Sensor Bahaya & Keamanan
        TuyaDevice("Sensor Asap Pintar\n(Smoke Detector)", R.drawable.ic_tuya_sensor_smoke, "Keamanan & Sensor", "Sensor Bahaya", listOf("smoke", "asap", "kebakaran", "alarm asap")),
        TuyaDevice("Sensor Kebocoran\nGas (LPG/CH4)", R.drawable.ic_tuya_sensor_smoke, "Keamanan & Sensor", "Sensor Bahaya", listOf("gas", "lpg", "kebocoran gas")),
        TuyaDevice("Sensor Karbon\nMonoksida (CO)", R.drawable.ic_tuya_sensor_smoke, "Keamanan & Sensor", "Sensor Bahaya", listOf("co", "karbon monoksida")),
        TuyaDevice("Sensor Kebocoran\nAir (Water Leak)", R.drawable.ic_tuya_sensor_water, "Keamanan & Sensor", "Sensor Bahaya", listOf("water", "leak", "bocor", "banjir")),
        TuyaDevice("Sensor Banjir\nBerkabel Probe", R.drawable.ic_tuya_sensor_water, "Keamanan & Sensor", "Sensor Bahaya", listOf("flood", "probe", "toren")),

        // Alarm & Darurat
        TuyaDevice("Sirine Alarm Wi-Fi", R.drawable.ic_tuya_alarm_siren, "Keamanan & Sensor", "Alarm", listOf("alarm", "sirene", "bunyi")),
        TuyaDevice("Sirine Outdoor\nSolar Strobe", R.drawable.ic_tuya_alarm_siren, "Keamanan & Sensor", "Alarm", listOf("solar", "strobe", "outdoor alarm")),
        TuyaDevice("Tombol Darurat\nSOS Panic Button", R.drawable.ic_tuya_sos_button, "Keamanan & Sensor", "Alarm", listOf("sos", "panic", "darurat", "lansia")),
        TuyaDevice("Sensor Getaran\nPintar (Vibration)", R.drawable.ic_tuya_sensor, "Keamanan & Sensor", "Alarm", listOf("vibration", "getaran", "gempa", "kaca")),

        // Water & Gas Valve
        TuyaDevice("Water valve controller\n(Gateway)", R.drawable.ic_tuya_water_valve, "Keamanan & Sensor", "Valve Controller", listOf("valve", "water", "kran", "katup")),
        TuyaDevice("Water valve controller\n(Wi-Fi)", R.drawable.ic_tuya_water_valve, "Keamanan & Sensor", "Valve Controller", listOf("valve", "water", "wifi")),
        TuyaDevice("Water valve controller\n(Zigbee)", R.drawable.ic_tuya_water_valve, "Keamanan & Sensor", "Valve Controller", listOf("valve", "zigbee")),
        TuyaDevice("Gas Valve\nController Pintar", R.drawable.ic_tuya_water_valve, "Keamanan & Sensor", "Valve Controller", listOf("gas", "valve", "tutup gas")),

        // ========================================================
        // 4. LARGE HOME APPLIANCES (Tuya Developer Platform)
        // ========================================================
        // Air Conditioner
        TuyaDevice("Air Conditioner\nPintar", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("ac", "air conditioner", "pendingin")),
        TuyaDevice("AC Inverter Pintar", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("ac", "inverter", "hemat listrik")),
        TuyaDevice("AC Portabel Pintar", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("portable", "ac portable")),
        TuyaDevice("Thermostat AC\nPintar", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("ac", "thermostat", "suhu")),
        TuyaDevice("Thermostat Sentral\nHVAC", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("hvac", "sentral", "ducting")),
        TuyaDevice("Kontroler AC\nVRF/VRV", R.drawable.ic_tuya_appliance, "Large Home Appliances", "Air Conditioner", listOf("vrf", "vrv", "central ac")),

        // Refrigerator
        TuyaDevice("Kulkas Pintar\nMulti-Door", R.drawable.ic_tuya_refrigerator, "Large Home Appliances", "Refrigerator", listOf("kulkas", "refrigerator", "freezer")),
        TuyaDevice("Kulkas Mini\nPintar", R.drawable.ic_tuya_refrigerator, "Large Home Appliances", "Refrigerator", listOf("kulkas mini", "bar", "kulkas kecil")),
        TuyaDevice("Showcase Pendingin\nMinuman", R.drawable.ic_tuya_refrigerator, "Large Home Appliances", "Refrigerator", listOf("showcase", "cooler", "minuman")),
        TuyaDevice("Wine Cooler\nPintar", R.drawable.ic_tuya_refrigerator, "Large Home Appliances", "Refrigerator", listOf("wine", "cooler", "rak anggur")),
        TuyaDevice("Chest Freezer\nPintar", R.drawable.ic_tuya_refrigerator, "Large Home Appliances", "Refrigerator", listOf("freezer", "chest freezer", "pembeku")),

        // Washing Machine & Dryer
        TuyaDevice("Mesin Cuci Pintar\n(Front Load)", R.drawable.ic_tuya_washing_machine, "Large Home Appliances", "Washing Machine", listOf("mesin cuci", "front load", "washer")),
        TuyaDevice("Mesin Cuci Pintar\n(Top Load)", R.drawable.ic_tuya_washing_machine, "Large Home Appliances", "Washing Machine", listOf("mesin cuci", "top load")),
        TuyaDevice("Pengering Pakaian\nPintar (Dryer)", R.drawable.ic_tuya_washing_machine, "Large Home Appliances", "Washing Machine", listOf("dryer", "pengering", "baju")),
        TuyaDevice("Mesin Cuci & Kering\nCombo", R.drawable.ic_tuya_washing_machine, "Large Home Appliances", "Washing Machine", listOf("washer dryer", "combo", "cuci kering")),
        TuyaDevice("Mesin Cuci Sepatu\nPintar", R.drawable.ic_tuya_washing_machine, "Large Home Appliances", "Washing Machine", listOf("shoe washer", "cuci sepatu")),

        // Water Heater
        TuyaDevice("Pemanas Air Listrik\nPintar", R.drawable.ic_tuya_water_heater, "Large Home Appliances", "Water Heater", listOf("water heater", "pemanas air", "listrik")),
        TuyaDevice("Pemanas Air Gas\nPintar", R.drawable.ic_tuya_water_heater, "Large Home Appliances", "Water Heater", listOf("water heater", "gas", "pemanas")),
        TuyaDevice("Heat Pump Water\nHeater Pintar", R.drawable.ic_tuya_water_heater, "Large Home Appliances", "Water Heater", listOf("heat pump", "water heater")),
        TuyaDevice("Pemanas Air Instan\nDigital", R.drawable.ic_tuya_water_heater, "Large Home Appliances", "Water Heater", listOf("instant", "water heater instan")),

        // ========================================================
        // 5. SMALL HOME APPLIANCES (Tuya Developer Platform)
        // ========================================================
        // Kipas & Ventilasi
        TuyaDevice("Kipas Angin Pintar", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("fan", "kipas", "berdiri")),
        TuyaDevice("Tower Fan Pintar", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("tower fan", "kipas menara")),
        TuyaDevice("Bladeless Fan\nPintar", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("bladeless", "tanpa baling")),
        TuyaDevice("Kipas Angin Plafon\n(Ceiling Fan)", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("ceiling fan", "plafon")),
        TuyaDevice("Kipas Exhaust\nDinding Pintar", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("exhaust", "ventilasi")),
        TuyaDevice("Kipas Sirkulasi\nUdara 3D", R.drawable.ic_tuya_fan, "Small Home Appliances", "Kipas & Ventilasi", listOf("circulator", "sirkulasi")),

        // Air Quality & Treatment
        TuyaDevice("Air Purifier Pintar", R.drawable.ic_tuya_air_purifier, "Small Home Appliances", "Air Quality", listOf("purifier", "pembersih udara", "hepa")),
        TuyaDevice("Humidifier Pintar", R.drawable.ic_tuya_diffuser, "Small Home Appliances", "Air Quality", listOf("humidifier", "pelembab", "mist")),
        TuyaDevice("Dehumidifier Pintar", R.drawable.ic_tuya_air_purifier, "Small Home Appliances", "Air Quality", listOf("dehumidifier", "penyerap lembab")),
        TuyaDevice("Aroma Diffuser\nAromaterapi", R.drawable.ic_tuya_diffuser, "Small Home Appliances", "Air Quality", listOf("diffuser", "aroma", "essential oil")),
        TuyaDevice("Pemanas Ruangan\nPintar (Heater)", R.drawable.ic_tuya_appliance, "Small Home Appliances", "Air Quality", listOf("heater", "pemanas ruangan")),

        // Cleaning & Robot
        TuyaDevice("Robot Vacuum\nCleaner", R.drawable.ic_tuya_robot_vacuum, "Small Home Appliances", "Cleaning & Robot", listOf("robot", "vacuum", "sapu")),
        TuyaDevice("Robot Pel Pintar\n(Mopping Robot)", R.drawable.ic_tuya_robot_vacuum, "Small Home Appliances", "Cleaning & Robot", listOf("pel", "mopping", "robot")),
        TuyaDevice("Window Cleaning\nRobot", R.drawable.ic_tuya_robot_vacuum, "Small Home Appliances", "Cleaning & Robot", listOf("window", "kaca", "robot pembersih")),
        TuyaDevice("Vacuum Tanpa Kabel\nPintar", R.drawable.ic_tuya_robot_vacuum, "Small Home Appliances", "Cleaning & Robot", listOf("cordless", "vacuum genggam")),
        TuyaDevice("Pet Grooming\nVacuum Pintar", R.drawable.ic_tuya_pet_feeder, "Small Home Appliances", "Cleaning & Robot", listOf("pet grooming", "bulu")),

        // Radiator & Heating
        TuyaDevice("TRV", R.drawable.ic_tuya_trv, "Small Home Appliances", "Radiator & Heating", listOf("trv", "thermostat", "radiator")),
        TuyaDevice("TRV\n(Gateway)", R.drawable.ic_tuya_trv, "Small Home Appliances", "Radiator & Heating", listOf("trv", "gateway", "zigbee")),
        TuyaDevice("Pemanas Lantai\nListrik (Thermostat)", R.drawable.ic_tuya_trv, "Small Home Appliances", "Radiator & Heating", listOf("underfloor", "lantai")),

        // Perawatan Pakaian
        TuyaDevice("Garment Steamer\nPintar", R.drawable.ic_tuya_washing_machine, "Small Home Appliances", "Perawatan Pakaian", listOf("steamer", "setrika uap")),
        TuyaDevice("Jemuran Baju\nElektrik Pintar", R.drawable.ic_tuya_curtain, "Small Home Appliances", "Perawatan Pakaian", listOf("clothes airer", "jemuran otomatis")),

        // ========================================================
        // 6. DAPUR (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Cooking
        TuyaDevice("Smart Rice Cooker", R.drawable.ic_tuya_rice_cooker, "Dapur", "Cooking", listOf("rice cooker", "nasi", "penanak")),
        TuyaDevice("Smart Air Fryer", R.drawable.ic_tuya_air_fryer, "Dapur", "Cooking", listOf("air fryer", "goreng", "gorengan")),
        TuyaDevice("Microwave Pintar", R.drawable.ic_tuya_steam_oven, "Dapur", "Cooking", listOf("microwave", "penghangat")),
        TuyaDevice("Steam Oven", R.drawable.ic_tuya_steam_oven, "Dapur", "Cooking", listOf("steam oven", "oven", "panggang")),
        TuyaDevice("Kompor Induksi\nPintar", R.drawable.ic_tuya_air_fryer, "Dapur", "Cooking", listOf("induction", "kompor induksi")),
        TuyaDevice("Multi-Cooker\nPintar", R.drawable.ic_tuya_rice_cooker, "Dapur", "Cooking", listOf("multicooker", "pressure cooker", "presto")),
        TuyaDevice("Sous Vide Cooker", R.drawable.ic_tuya_rice_cooker, "Dapur", "Cooking", listOf("sous vide", "masak lambat")),

        // Beverage
        TuyaDevice("Ketel Listrik Pintar", R.drawable.ic_tuya_kettle, "Dapur", "Beverage", listOf("kettle", "ketel", "ceret", "teko")),
        TuyaDevice("Mesin Kopi Pintar\n(Espresso)", R.drawable.ic_tuya_coffee_machine, "Dapur", "Beverage", listOf("coffee", "kopi", "espresso")),
        TuyaDevice("Dispenser Air\nPanas/Dingin", R.drawable.ic_tuya_kettle, "Dapur", "Beverage", listOf("dispenser", "air minum")),

        // Prep & Dishwashing
        TuyaDevice("Blender Pintar", R.drawable.ic_tuya_coffee_machine, "Dapur", "Prep & Dishwashing", listOf("blender", "jus")),
        TuyaDevice("Juicer Pintar", R.drawable.ic_tuya_coffee_machine, "Dapur", "Prep & Dishwashing", listOf("juicer", "slow juicer")),
        TuyaDevice("Dishwasher Pintar\n(Cuci Piring)", R.drawable.ic_tuya_washing_machine, "Dapur", "Prep & Dishwashing", listOf("dishwasher", "cuci piring")),
        TuyaDevice("Probe Suhu Daging\n(Meat Thermometer)", R.drawable.ic_tuya_sensor, "Dapur", "Prep & Dishwashing", listOf("meat probe", "suhu daging", "bbq")),

        // ========================================================
        // 7. EXERCISE & HEALTH (Tuya Developer Platform)
        // ========================================================
        // Body Scale & Fitness
        TuyaDevice("Timbangan Badan\nPintar (Body Scale)", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Body & Fitness", listOf("scale", "timbangan", "lemak", "bmi")),
        TuyaDevice("Tali Skipping\nDigital Pintar", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Body & Fitness", listOf("jump rope", "skipping", "lompat tali")),
        TuyaDevice("Treadmill Pintar", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Body & Fitness", listOf("treadmill", "lari")),
        TuyaDevice("Sepeda Statis\nPintar (Spin Bike)", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Body & Fitness", listOf("spin bike", "sepeda statis")),
        TuyaDevice("Smart Dumbbell\nDigital", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Body & Fitness", listOf("dumbbell", "barbel")),

        // Health & Medical
        TuyaDevice("Tensimeter Digital\nPintar (BP)", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Health & Medical", listOf("tensi", "blood pressure", "tekanan darah")),
        TuyaDevice("Pulse Oximeter\nPintar (SpO2)", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Health & Medical", listOf("oximeter", "spo2", "oksigen")),
        TuyaDevice("Termometer Dahi\nPintar", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Health & Medical", listOf("thermometer", "suhu tubuh", "dahi")),
        TuyaDevice("Ear Wax Removal\nOtoscope", R.drawable.ic_tuya_ear_otoscope, "Exercise & Health", "Health & Medical", listOf("ear", "wax", "otoscope", "telinga")),
        TuyaDevice("Glukometer Darah\nPintar", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Health & Medical", listOf("glucose", "gula darah")),
        TuyaDevice("Monitor Kualitas\nTidur Pintar", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Health & Medical", listOf("sleep", "tidur", "sleep tracker")),

        // Personal Care
        TuyaDevice("Sikat Gigi Elektrik\nPintar", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Personal Care", listOf("toothbrush", "sikat gigi")),
        TuyaDevice("Smart Massage\nGun", R.drawable.ic_tuya_blood_pressure, "Exercise & Health", "Personal Care", listOf("massage gun", "pijat")),
        TuyaDevice("Pijat Kaki Pintar\n(Foot Massager)", R.drawable.ic_tuya_body_scale, "Exercise & Health", "Personal Care", listOf("foot massager", "pijat kaki")),

        // ========================================================
        // 8. KAMERA & KUNCI (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Security Camera
        TuyaDevice("Smart Indoor\nCamera", R.drawable.ic_tuya_camera_indoor, "Kamera & Kunci", "Security Camera", listOf("camera", "cctv", "indoor", "dalam ruangan")),
        TuyaDevice("Smart Outdoor\nCamera (Bullet)", R.drawable.ic_tuya_camera_outdoor, "Kamera & Kunci", "Security Camera", listOf("camera", "outdoor", "bullet", "luar")),
        TuyaDevice("Smart PTZ\nCamera (360°)", R.drawable.ic_tuya_camera_ptz, "Kamera & Kunci", "Security Camera", listOf("camera", "ptz", "putar", "360")),
        TuyaDevice("Smart Battery\nCamera (Wire-free)", R.drawable.ic_tuya_camera_indoor, "Kamera & Kunci", "Security Camera", listOf("camera", "battery", "baterai", "tanpa kabel")),
        TuyaDevice("Smart 4G LTE\nSolar Camera", R.drawable.ic_tuya_camera_outdoor, "Kamera & Kunci", "Security Camera", listOf("camera", "4g", "solar", "lte", "kebun")),
        TuyaDevice("Baby Monitor\nPintar", R.drawable.ic_tuya_camera_indoor, "Kamera & Kunci", "Security Camera", listOf("baby monitor", "bayi", "tangisan")),
        TuyaDevice("Floodlight Camera\nOutdoor", R.drawable.ic_tuya_camera_outdoor, "Kamera & Kunci", "Security Camera", listOf("floodlight", "camera", "sorot cctv")),

        // Doorbell
        TuyaDevice("Smart Video\nDoorbell (Wi-Fi)", R.drawable.ic_tuya_doorbell, "Kamera & Kunci", "Doorbell", listOf("doorbell", "bel pintu", "video bell")),
        TuyaDevice("Smart Video\nDoorbell (Baterai)", R.drawable.ic_tuya_doorbell, "Kamera & Kunci", "Doorbell", listOf("doorbell", "baterai", "bel")),
        TuyaDevice("Chime Bel Pintu\nPintar", R.drawable.ic_tuya_doorbell, "Kamera & Kunci", "Doorbell", listOf("chime", "bel", "speaker")),

        // Smart Door Lock
        TuyaDevice("Smart Door Lock\n(Wi-Fi)", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("lock", "kunci pintu", "fingerprint", "sidik jari")),
        TuyaDevice("Smart Door Lock\n(Zigbee)", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("lock", "zigbee", "kunci")),
        TuyaDevice("Smart Door Lock\n(Bluetooth)", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("lock", "ble", "bluetooth")),
        TuyaDevice("Smart Lock 3D\nFace Recognition", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("face", "wajah", "3d face", "kunci muka")),
        TuyaDevice("Smart Glass Door\nLock (Pintu Kaca)", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("glass", "kaca", "kantor")),
        TuyaDevice("Smart Door Handle\nLock", R.drawable.ic_tuya_lock_door, "Kamera & Kunci", "Smart Lock", listOf("handle", "gagang pintu")),
        TuyaDevice("Smart Padlock\n(Gembok Sidik Jari)", R.drawable.ic_tuya_lock_padlock, "Kamera & Kunci", "Smart Lock", listOf("padlock", "gembok", "fingerprint")),
        TuyaDevice("Smart Key Box\nPintar", R.drawable.ic_tuya_lock_padlock, "Kamera & Kunci", "Smart Lock", listOf("key box", "kotak kunci", "airbnb")),
        TuyaDevice("Kunci Laci &\nLemari Pintar", R.drawable.ic_tuya_lock_padlock, "Kamera & Kunci", "Smart Lock", listOf("drawer", "laci", "lemari", "cabinet")),

        // ========================================================
        // 9. GATEWAY CONTROL (Tuya Developer Platform)
        // ========================================================
        // Gateway Hub
        TuyaDevice("Multi-Mode\nGateway (Zigbee+BLE)", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("gateway", "multimode", "zigbee", "ble", "hub")),
        TuyaDevice("Wireless Gateway\n(Zigbee)", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("gateway", "zigbee", "hub")),
        TuyaDevice("Wired Gateway\n(Ethernet LAN)", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("gateway", "ethernet", "lan", "kabel")),
        TuyaDevice("Bluetooth Mesh\nHub", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("mesh", "ble mesh", "gateway")),
        TuyaDevice("Matter & Thread\nGateway Hub", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("matter", "thread", "apple home", "hub")),
        TuyaDevice("Sub-1GHz Gateway\nHub", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Gateway Hub", listOf("sub-1g", "433mhz", "868mhz")),

        // Repeater & Extender
        TuyaDevice("Zigbee Signal\nRepeater", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Repeater & Extender", listOf("repeater", "penguat sinyal", "extender")),
        TuyaDevice("BLE Mesh Signal\nExtender", R.drawable.ic_tuya_gateway_hub, "Gateway Control", "Repeater & Extender", listOf("extender", "mesh repeater")),

        // Universal Remote
        TuyaDevice("Smart IR Blaster", R.drawable.ic_tuya_ir_remote, "Gateway Control", "Universal Remote", listOf("ir", "remote", "blaster", "infra merah")),
        TuyaDevice("Smart IR + RF\n433MHz Remote", R.drawable.ic_tuya_ir_remote, "Gateway Control", "Universal Remote", listOf("ir rf", "433", "remote universal")),

        // ========================================================
        // 10. OUTDOOR TRAVEL (Tuya Developer Platform)
        // ========================================================
        // Vehicle Tracker & Mobility
        TuyaDevice("GPS Vehicle Tracker\n(LTE Cat.1)", R.drawable.ic_tuya_gps_tracker, "Outdoor Travel", "Vehicle & Mobility", listOf("gps", "tracker", "pelacak", "mobil")),
        TuyaDevice("Smart Motorcycle\nTracker", R.drawable.ic_tuya_gps_tracker, "Outdoor Travel", "Vehicle & Mobility", listOf("gps", "motor", "pelacak motor")),
        TuyaDevice("Skuter Listrik\nPintar", R.drawable.ic_tuya_gps_tracker, "Outdoor Travel", "Vehicle & Mobility", listOf("scooter", "skuter")),
        TuyaDevice("Sepeda Listrik\nPintar (E-Bike)", R.drawable.ic_tuya_gps_tracker, "Outdoor Travel", "Vehicle & Mobility", listOf("ebike", "sepeda listrik")),

        // Pet Care
        TuyaDevice("Smart Pet Feeder\n(Kamera & Suara)", R.drawable.ic_tuya_pet_feeder, "Outdoor Travel", "Pet Care", listOf("pet", "feeder", "pakan", "hewan", "kucing", "anjing")),
        TuyaDevice("Smart Pet Water\nFountain", R.drawable.ic_tuya_pet_feeder, "Outdoor Travel", "Pet Care", listOf("water", "pet", "minum hewan", "fountain")),
        TuyaDevice("Kotak Pasir Kucing\nOtomatis", R.drawable.ic_tuya_pet_feeder, "Outdoor Travel", "Pet Care", listOf("cat litter", "pasir kucing", "self cleaning")),

        // Outdoor Power & Camping
        TuyaDevice("Portable Power\nStation", R.drawable.ic_tuya_energy, "Outdoor Travel", "Power & Camping", listOf("power station", "genset baterai", "camping")),
        TuyaDevice("Lampu Tenda\nBerkemah Pintar", R.drawable.ic_tuya_bulb_rgb, "Outdoor Travel", "Power & Camping", listOf("tent lamp", "tenda", "kemah")),
        TuyaDevice("Sensor Kolam\nRenang Pintar", R.drawable.ic_tuya_sensor, "Outdoor Travel", "Power & Camping", listOf("pool sensor", "klorin", "ph kolam")),

        // ========================================================
        // 11. ENERGY (Tuya Developer Platform)
        // ========================================================
        // Energy Meter
        TuyaDevice("Smart Electricity\nMeter (1-Fase)", R.drawable.ic_tuya_energy_meter, "Energy", "Energy Meter", listOf("meter", "kwh", "1 phase", "listrik")),
        TuyaDevice("Smart Electricity\nMeter (3-Fase)", R.drawable.ic_tuya_energy_meter, "Energy", "Energy Meter", listOf("meter", "kwh", "3 phase", "industri")),
        TuyaDevice("Bi-directional\nSolar Energy Meter", R.drawable.ic_tuya_energy_meter, "Energy", "Energy Meter", listOf("solar meter", "bi-directional", "ekspor impor")),
        TuyaDevice("CT Clamp Energy\nMeter Pintar", R.drawable.ic_tuya_energy_meter, "Energy", "Energy Meter", listOf("clamp", "ct clamp", "arus")),

        // Circuit Breaker
        TuyaDevice("Smart Circuit\nBreaker (MCB)", R.drawable.ic_tuya_breaker, "Energy", "Circuit Breaker", listOf("breaker", "mcb", "pemutus")),
        TuyaDevice("Smart RCBO\nLeakage Breaker", R.drawable.ic_tuya_breaker, "Energy", "Circuit Breaker", listOf("rcbo", "elcb", "kebocoran arus")),
        TuyaDevice("Smart Isolator\nSwitch", R.drawable.ic_tuya_breaker, "Energy", "Circuit Breaker", listOf("isolator", "saklar utama")),

        // Solar & EV
        TuyaDevice("Solar Inverter\nPintar", R.drawable.ic_tuya_solar_inverter, "Energy", "Solar & EV", listOf("solar", "inverter", "plts", "tenaga surya")),
        TuyaDevice("Micro Inverter\nPintar", R.drawable.ic_tuya_solar_inverter, "Energy", "Solar & EV", listOf("micro inverter", "panel surya")),
        TuyaDevice("Smart EV Charger\nWallbox (7kW/11kW)", R.drawable.ic_tuya_ev_charger, "Energy", "Solar & EV", listOf("ev charger", "wallbox", "mobil listrik")),
        TuyaDevice("Portable EV\nCharger Pintar", R.drawable.ic_tuya_ev_charger, "Energy", "Solar & EV", listOf("portable ev", "charger mobil")),

        // ========================================================
        // 12. ENTERTAINMENT (Tuya Developer Platform / SmartLife)
        // ========================================================
        // Television & Video
        TuyaDevice("televisi", R.drawable.ic_tuya_tv, "Entertainment", "Television & Video", listOf("tv", "television", "layar", "smart tv")),
        TuyaDevice("Smart Android\nTV Box", R.drawable.ic_tuya_tv, "Entertainment", "Television & Video", listOf("tv box", "android tv", "media player")),
        TuyaDevice("Smart Projector", R.drawable.ic_tuya_projector, "Entertainment", "Television & Video", listOf("projector", "proyektor", "bioskop")),
        TuyaDevice("Layar Proyektor\nElektrik Pintar", R.drawable.ic_tuya_curtain, "Entertainment", "Television & Video", listOf("projector screen", "layar")),

        // Audio & Control
        TuyaDevice("Smart Soundbar", R.drawable.ic_tuya_remote_control, "Entertainment", "Audio & Control", listOf("soundbar", "speaker", "audio")),
        TuyaDevice("Smart Hi-Fi\nWireless Speaker", R.drawable.ic_tuya_remote_control, "Entertainment", "Audio & Control", listOf("speaker", "hifi", "musik")),
        TuyaDevice("Remote control universal\n(Wi-Fi+BLE)", R.drawable.ic_tuya_ir_remote, "Entertainment", "Audio & Control", listOf("remote", "universal", "ir", "infrared")),
        TuyaDevice("Smart IR Blaster", R.drawable.ic_tuya_ir_remote, "Entertainment", "Audio & Control", listOf("ir", "blaster", "remote ac tv")),
        TuyaDevice("PC Power Switch\nCard (PCIe Wi-Fi)", R.drawable.ic_tuya_remote_control, "Entertainment", "Audio & Control", listOf("pc power", "pcie", "remote komputer")),

        // ========================================================
        // 13. INDUSTRY & AGRICULTURE (Tuya Developer Platform)
        // ========================================================
        // Agriculture
        TuyaDevice("Sensor Kelembaban\nTanah Pintar", R.drawable.ic_tuya_sensor, "Industry & Agriculture", "Agriculture", listOf("soil", "tanah", "kelembaban tanah", "npk")),
        TuyaDevice("Smart Irrigation\nWater Valve Timer", R.drawable.ic_tuya_water_valve, "Industry & Agriculture", "Agriculture", listOf("irrigation", "irigasi", "penyiram", "taman")),
        TuyaDevice("Greenhouse Climate\nController", R.drawable.ic_tuya_sensor, "Industry & Agriculture", "Agriculture", listOf("greenhouse", "rumah kaca", "hidroponik")),
        TuyaDevice("Fertigation Dosing\nController", R.drawable.ic_tuya_water_valve, "Industry & Agriculture", "Agriculture", listOf("pupuk", "dosing", "fertigasi")),

        // Weather & Tank
        TuyaDevice("Smart Weather\nStation Pro", R.drawable.ic_tuya_sensor, "Industry & Agriculture", "Weather & Tank", listOf("weather", "cuaca", "angin", "hujan", "uv")),
        TuyaDevice("Sensor Tangki Air\nUltrasonic", R.drawable.ic_tuya_sensor_water, "Industry & Agriculture", "Weather & Tank", listOf("water level", "tangki air", "toren", "ultrasonic")),

        // Industrial
        TuyaDevice("Smart Industrial\nModbus RS485 Hub", R.drawable.ic_tuya_gateway_hub, "Industry & Agriculture", "Industrial", listOf("modbus", "rs485", "industrial")),
        TuyaDevice("Smart 3-Phase\nMotor Controller", R.drawable.ic_tuya_breaker, "Industry & Agriculture", "Industrial", listOf("motor", "3 phase", "pompa")),

        // ========================================================
        // 14. LAINNYA (Tuya Developer Platform / SmartLife)
        // ========================================================
        TuyaDevice("Fingerbot Pintar\n(Mechanical Pusher)", R.drawable.ic_tuya_fingerbot, "Lainnya", "Robotic & Automation", listOf("fingerbot", "robot penekan", "saklar mekanik")),
        TuyaDevice("Smart Curtain\nRobot", R.drawable.ic_tuya_curtain, "Lainnya", "Robotic & Automation", listOf("curtain robot", "robot tirai")),
        TuyaDevice("Smart Tag Finder\n(Anti-Hilang BLE)", R.drawable.ic_tuya_gps_tracker, "Lainnya", "Robotic & Automation", listOf("tag", "finder", "pelacak kunci", "airtag")),
        TuyaDevice("Pembuka Jendela\nOtomatis Pintar", R.drawable.ic_tuya_curtain, "Lainnya", "Robotic & Automation", listOf("window opener", "jendela otomatis")),
        TuyaDevice("Mesin Pewangi\nOtomatis Pintar", R.drawable.ic_tuya_diffuser, "Lainnya", "Robotic & Automation", listOf("fragrance", "parfum", "pewangi")),
        TuyaDevice("Brankas Pintar\n(Smart Safe Box)", R.drawable.ic_tuya_lock_door, "Lainnya", "Robotic & Automation", listOf("safe box", "brankas", "kunci brankas"))
    )

    private var selectedCategory: String = "Frequently Used"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fineLocation && !coarseLocation) {
            Toast.makeText(this, "Izin lokasi diperlukan untuk memindai perangkat IoT sekitar.", Toast.LENGTH_SHORT).show()
        }
        checkGpsService()
        startDiscovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCleanWhiteChrome()

        homeId = intent.getLongExtra("HOME_ID", 0L)
        if (homeId == 0L) {
            homeId = deviceRepo.getCurrentHomeId()
        }

        setupHeader()
        setupSegmentTabs()
        setupCategoryTabs()
        renderCatalog()
        setupSearchMode()
        setupBackHandling()
        checkInitialPermissions()
        setupAutoDiscovery()
    }

    private fun setupCleanWhiteChrome() {
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.flCenterHub.outlineSpotShadowColor = 0x263858FF
            binding.flCenterHub.outlineAmbientShadowColor = 0x103858FF
            binding.btnConnectAllDevices.outlineSpotShadowColor = 0x553858FF
            binding.btnConnectAllDevices.outlineAmbientShadowColor = 0x243858FF
        }
    }

    private fun setupSegmentTabs() {
        binding.tabNearbyDevices.setOnClickListener { selectPairTab(nearby = true) }
        binding.tabAddManual.setOnClickListener { selectPairTab(nearby = false) }
        selectPairTab(nearby = true)
    }

    private fun selectPairTab(nearby: Boolean) {
        isNearbyTabSelected = nearby
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (nearby) {
            binding.tabNearbyDevices.setBackgroundResource(R.drawable.bg_pair_tab_active)
            binding.tabNearbyDevices.setTextColor(Color.WHITE)
            binding.tabAddManual.background = null
            binding.tabAddManual.setTextColor(Color.parseColor("#4B5563"))
            binding.layoutNearbyMode.visibility = View.VISIBLE
            binding.layoutManualMode.visibility = View.GONE
            binding.radarScanView.startScan()
        } else {
            binding.tabAddManual.setBackgroundResource(R.drawable.bg_pair_tab_active)
            binding.tabAddManual.setTextColor(Color.WHITE)
            binding.tabNearbyDevices.background = null
            binding.tabNearbyDevices.setTextColor(Color.parseColor("#4B5563"))
            binding.layoutNearbyMode.visibility = View.GONE
            binding.layoutManualMode.visibility = View.VISIBLE
            binding.radarScanView.stopScan()
        }
    }

    private fun checkGpsService() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val isGpsEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                    lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            if (!isGpsEnabled) {
                Toast.makeText(this, "Pastikan Lokasi (GPS) dan Bluetooth aktif di pengaturan HP agar perangkat sekitar terdeteksi.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Log.w("PairDeviceActivity", "Error checking GPS: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (isNearbyTabSelected) {
            binding.radarScanView.startScan()
        }
        if (!isPairingDialogOpen && !isTuyaScanning) {
            startRealBleScan()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelAutoAdd()
        stopBleScan()
        binding.radarScanView.stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }

    private fun setupHeader() {
        binding.btnBackPairing.setOnClickListener {
            finish()
        }

        binding.btnTopScanQr.setOnClickListener {
            Toast.makeText(this, "Pemindai QR: Arahkan kamera ke kode QR / barcode pada kotak perangkat", Toast.LENGTH_SHORT).show()
        }

        val showPairingTips = View.OnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mode Pairing Perangkat")
                .setMessage("1. Pastikan Wi-Fi (2.4 GHz), Bluetooth, dan Lokasi (GPS) aktif di HP Anda.\n2. Tekan & tahan tombol reset/daya pada perangkat selama 5 detik hingga lampu indikator berkedip cepat.\n3. Jika perangkat belum muncul di radar, gunakan tab Add Manual untuk memilih kategori perangkat.")
                .setPositiveButton("Pilih Manual") { _, _ -> selectPairTab(nearby = false) }
                .setNegativeButton("Tutup", null)
                .show()
        }
        binding.layoutWifiBtPill.setOnClickListener(showPairingTips)
        binding.tvLearnMore.setOnClickListener(showPairingTips)

        binding.btnConnectAllDevices.setOnClickListener { v ->
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
            }.start()

            if (discoveredDevices.isNotEmpty()) {
                cancelAutoAdd()
                if (discoveredDevices.size == 1) {
                    openDiscoveredDevice(discoveredDevices.first())
                } else {
                    val names = discoveredDevices.map { it.name.replace("\n", " ") }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Pilih Perangkat untuk Dihubungkan (${discoveredDevices.size})")
                        .setItems(names) { _, which ->
                            discoveredDevices.getOrNull(which)?.let { openDiscoveredDevice(it) }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            } else {
                startDiscovery()
                Toast.makeText(this, "Memindai ulang perangkat di sekitar... Pastikan perangkat dalam mode pairing.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun setupCategoryTabs() {
        binding.layoutCategoryTabsContainer.removeAllViews()

        for (category in categories) {
            val tabBinding = ItemTuyaCategoryTabBinding.inflate(layoutInflater, binding.layoutCategoryTabsContainer, false)
            tabBinding.tvCategoryName.text = category

            val isSelected = category == selectedCategory
            applyCategoryTabStyle(tabBinding, isSelected)

            tabBinding.root.setOnClickListener {
                selectedCategory = category
                refreshCategoryTabsSelection()
                renderCatalog()
                binding.scrollDeviceCatalog.scrollTo(0, 0)
            }

            binding.layoutCategoryTabsContainer.addView(tabBinding.root)
        }
    }

    private fun refreshCategoryTabsSelection() {
        val boldFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_bold)
        val mediumFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_medium)
        for (i in 0 until binding.layoutCategoryTabsContainer.childCount) {
            val child = binding.layoutCategoryTabsContainer.getChildAt(i)
            val category = categories.getOrNull(i) ?: continue
            val isSelected = category == selectedCategory

            val tv = child.findViewById<TextView>(R.id.tvCategoryName)
            val indicator = child.findViewById<View>(R.id.viewTabIndicator)
            if (isSelected) {
                child.setBackgroundColor(Color.WHITE)
                indicator?.visibility = View.VISIBLE
                tv?.setTextColor(Color.parseColor("#3858FF"))
                tv?.typeface = boldFont ?: Typeface.DEFAULT_BOLD
            } else {
                child.setBackgroundColor(Color.parseColor("#F3F5F9"))
                indicator?.visibility = View.INVISIBLE
                tv?.setTextColor(Color.parseColor("#666B75"))
                tv?.typeface = mediumFont ?: Typeface.DEFAULT
            }
        }
    }

    private fun applyCategoryTabStyle(tabBinding: ItemTuyaCategoryTabBinding, isSelected: Boolean) {
        val boldFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_bold)
        val mediumFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_medium)
        if (isSelected) {
            tabBinding.root.setBackgroundColor(Color.WHITE)
            tabBinding.viewTabIndicator.visibility = View.VISIBLE
            tabBinding.tvCategoryName.setTextColor(Color.parseColor("#3858FF"))
            tabBinding.tvCategoryName.typeface = boldFont ?: Typeface.DEFAULT_BOLD
        } else {
            tabBinding.root.setBackgroundColor(Color.parseColor("#F3F5F9"))
            tabBinding.viewTabIndicator.visibility = View.INVISIBLE
            tabBinding.tvCategoryName.setTextColor(Color.parseColor("#666B75"))
            tabBinding.tvCategoryName.typeface = mediumFont ?: Typeface.DEFAULT
        }
    }

    private fun renderCatalog() {
        binding.layoutDeviceCatalogContainer.removeAllViews()

        val categoryDevices = allDeviceCatalog.filter { it.category == selectedCategory }
        val density = resources.displayMetrics.density
        val boldFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_bold)
        val semiBoldFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans_semibold)

        // Category Editorial Header Strip (Category Name + Model Count Pill)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
        }
        val tvCatTitle = TextView(this).apply {
            text = selectedCategory
            setTextColor(Color.parseColor("#191919"))
            textSize = 15f
            typeface = boldFont ?: Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvCatCount = TextView(this).apply {
            text = "${categoryDevices.size} model"
            setTextColor(Color.parseColor("#3858FF"))
            textSize = 11f
            typeface = semiBoldFont ?: Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_harmony_soft_blue_pill)
            setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
        }
        headerRow.addView(tvCatTitle)
        headerRow.addView(tvCatCount)
        binding.layoutDeviceCatalogContainer.addView(headerRow)

        if (categoryDevices.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Tidak ada perangkat pada kategori ini"
                setTextColor(Color.parseColor("#9398A2"))
                textSize = 13f
                setPadding(0, (32 * density).toInt(), 0, 0)
            }
            binding.layoutDeviceCatalogContainer.addView(tvEmpty)
            return
        }

        val grouped = categoryDevices.groupBy { it.subcategory }

        for ((subcategory, devices) in grouped) {
            // Borderless Pure White Grouped Subcategory Card (Huawei AI Life style)
            val groupCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_manual_group_card)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
                setPadding(
                    (10 * density).toInt(),
                    (12 * density).toInt(),
                    (10 * density).toInt(),
                    (4 * density).toInt()
                )
            }

            // Subcategory Header Row
            val subHeaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
                setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            }
            val tvSubHeader = TextView(this).apply {
                text = subcategory
                setTextColor(Color.parseColor("#191919"))
                textSize = 13f
                typeface = boldFont ?: Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvSubCount = TextView(this).apply {
                text = "${devices.size}"
                setTextColor(Color.parseColor("#9398A2"))
                textSize = 11f
                typeface = semiBoldFont ?: Typeface.DEFAULT
            }
            subHeaderRow.addView(tvSubHeader)
            subHeaderRow.addView(tvSubCount)
            groupCard.addView(subHeaderRow)

            // Equal 3-Column Grid Rows inside the card
            val chunks = devices.chunked(3)
            for (chunk in chunks) {
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                for (device in chunk) {
                    val itemBinding = ItemTuyaDeviceBinding.inflate(layoutInflater, rowLayout, false)
                    itemBinding.ivDeviceIcon.setImageResource(device.iconRes)
                    itemBinding.tvDeviceName.text = device.name

                    itemBinding.root.setOnClickListener {
                        openResetDeviceGuide(device)
                    }

                    rowLayout.addView(itemBinding.root)
                }

                val placeholdersNeeded = 3 - chunk.size
                for (p in 0 until placeholdersNeeded) {
                    val placeholder = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        visibility = View.INVISIBLE
                    }
                    rowLayout.addView(placeholder)
                }

                groupCard.addView(rowLayout)
            }

            binding.layoutDeviceCatalogContainer.addView(groupCard)
        }
    }

    private fun setupSearchMode() {
        // Trigger from Normal Mode search pill
        binding.btnOpenSearchMode.setOnClickListener {
            openSearchScreen()
        }

        binding.btnBackSearch.setOnClickListener {
            closeSearchScreen()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchQuery.setText("")
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun openSearchScreen() {
        binding.layoutNormalMode.visibility = View.GONE
        binding.layoutSearchMode.visibility = View.VISIBLE
        binding.etSearchQuery.requestFocus()
        showKeyboard(binding.etSearchQuery)
        performSearch(binding.etSearchQuery.text.toString().trim())
    }

    private fun closeSearchScreen() {
        hideKeyboard(binding.etSearchQuery)
        binding.etSearchQuery.setText("")
        binding.layoutSearchMode.visibility = View.GONE
        binding.layoutNormalMode.visibility = View.VISIBLE
    }

    private fun performSearch(query: String) {
        binding.layoutSearchResultsList.removeAllViews()

        if (query.isEmpty()) {
            binding.tvSearchEmpty.visibility = View.GONE
            return
        }

        val q = query.lowercase()

        // Match against device name, category, subcategory, and Tuya search keywords
        val matches = allDeviceCatalog.filter { dev ->
            dev.name.lowercase().contains(q) ||
                    dev.subcategory.lowercase().contains(q) ||
                    dev.category.lowercase().contains(q) ||
                    dev.searchKeywords.any { it.lowercase().contains(q) }
        }

        if (matches.isEmpty()) {
            binding.tvSearchEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvSearchEmpty.visibility = View.GONE

        for (device in matches) {
            val itemBinding = ItemTuyaSearchResultBinding.inflate(layoutInflater, binding.layoutSearchResultsList, false)
            itemBinding.ivSearchDeviceIcon.setImageResource(device.iconRes)
            itemBinding.tvSearchDeviceName.text = device.name.replace("\n", " ")
            itemBinding.tvSearchDeviceCategory.text = "${device.category} • ${device.subcategory}"

            itemBinding.root.setOnClickListener {
                openResetDeviceGuide(device)
            }

            binding.layoutSearchResultsList.addView(itemBinding.root)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutSearchMode.visibility == View.VISIBLE) {
                    closeSearchScreen()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupAutoDiscovery() {
        startDiscovery()
    }

    private fun startDiscovery() {
        stopDiscovery()
        isAutoAddCancelled = false

        binding.tvSearchingNearby.text = "Looking for nearby devices..."
        binding.tvAutoAddCountdown.visibility = View.GONE
        if (isNearbyTabSelected) {
            binding.radarScanView.startScan()
        }
        discoveredDevices.clear()
        cachedScanBeans.clear()
        renderedOrbitMap.clear()
        binding.flOrbitDevicesOverlay.removeAllViews()
        updateConnectButtonLabel()

        startRealBleScan()
    }

    private fun updateConnectButtonLabel() {
        val count = discoveredDevices.size
        binding.btnConnectAllDevices.text = when {
            count == 0 -> "Connect to All Devices"
            count == 1 -> "Connect Device (1)"
            else -> "Connect to All Devices ($count)"
        }
    }

    private fun startRealBleScan() {
        try {
            val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasScan) return

            // Official Tuya SDK BLE Scanner ONLY (no ScanType.NORMAL, no duplicate Android scanner)
            // Guarantees every ScanDeviceBean is a decrypted Tuya device registered in DeviceScanCache.mUnbindDeviceList
            val setting = LeScanSetting.Builder()
                .setTimeout(120000L)
                .addScanType(ScanType.SINGLE)
                .addScanType(ScanType.SINGLE_QR)
                .addScanType(ScanType.THING_BEACON)
                .addScanType(ScanType.SIG_MESH)
                .addScanType(ScanType.MESH)
                .setRepeatFilter(false)
                .build()

            ThingHomeSdk.getBleOperator().startLeScan(setting, object : BleScanResponse {
                override fun onResult(bean: ScanDeviceBean?) {
                    val uuid = bean?.uuid?.trim().orEmpty()
                    if (bean == null || uuid.isEmpty()) return

                    TuyaPairingRepository.cacheScannedDevice(bean)
                    cachedScanBeans[uuid] = bean

                    val cachedCloudInfo = TuyaPairingRepository.resolvedInfoByUuid[uuid]
                    val cloudName = cachedCloudInfo?.name?.trim().orEmpty()
                    val rawBeanName = bean.name?.trim().orEmpty()

                    val protocolLabel = if (bean.configType == BleConfigType.CONFIG_TYPE_SINGLE.type) {
                        "Bluetooth"
                    } else {
                        "Bluetooth & Wi-Fi"
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val existingIndex = discoveredDevices.indexOfFirst { it.idKey == uuid }

                        if (cloudName.isNotEmpty()) {
                            val classified = TuyaDeviceClassifier.classify(
                                rawName = cloudName,
                                productId = cachedCloudInfo?.productId ?: bean.productId
                            )
                            val updated = DiscoveredDevice(
                                idKey = uuid,
                                name = classified.displayName,
                                iconRes = classified.iconRes,
                                category = classified.category,
                                subcategory = classified.subcategory,
                                protocol = protocolLabel,
                                scanBean = bean
                            )
                            if (existingIndex >= 0) {
                                discoveredDevices[existingIndex] = updated
                            } else {
                                discoveredDevices.add(updated)
                            }
                        } else if (existingIndex >= 0) {
                            // Keep existing resolved name/icon while refreshing live ScanDeviceBean (latest address/rssi)
                            discoveredDevices[existingIndex] = discoveredDevices[existingIndex].copy(
                                protocol = protocolLabel,
                                scanBean = bean
                            )
                        } else {
                            val initialClassified = if (rawBeanName.isNotEmpty() && !rawBeanName.equals("null", true)) {
                                TuyaDeviceClassifier.classify(rawName = rawBeanName, productId = bean.productId)
                            } else {
                                null
                            }
                            discoveredDevices.add(
                                DiscoveredDevice(
                                    idKey = uuid,
                                    name = initialClassified?.displayName ?: "Smart Device",
                                    iconRes = initialClassified?.iconRes ?: R.drawable.ic_tuya_wifi_3ch_switch,
                                    category = initialClassified?.category ?: "Electrical",
                                    subcategory = initialClassified?.subcategory ?: "Switch",
                                    protocol = protocolLabel,
                                    scanBean = bean
                                )
                            )
                        }
                        renderDiscoveredDevices()
                    }

                    // Resolve real product name & icon from Tuya Cloud (e.g., "Security Camera", "WiFi 3ch switch")
                    if (cachedCloudInfo == null) {
                        TuyaPairingRepository.resolveScannedDeviceInfo(bean) { info ->
                            val resolvedName = info?.name?.trim().orEmpty()
                            if (resolvedName.isEmpty()) return@resolveScannedDeviceInfo
                            val classified = TuyaDeviceClassifier.classify(
                                rawName = resolvedName,
                                productId = info?.productId ?: bean.productId
                            )
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                val idx = discoveredDevices.indexOfFirst { it.idKey == uuid }
                                val latestBean = cachedScanBeans[uuid] ?: bean
                                if (idx >= 0) {
                                    discoveredDevices[idx] = discoveredDevices[idx].copy(
                                        name = classified.displayName,
                                        iconRes = classified.iconRes,
                                        category = classified.category,
                                        subcategory = classified.subcategory,
                                        scanBean = latestBean
                                    )
                                    renderDiscoveredDevices()
                                }
                            }
                        }
                    }
                }
            })
            isTuyaScanning = true
        } catch (e: Exception) {
            Log.d("PairDeviceActivity", "BLE scan init error: ${e.message}")
        }
    }

    private fun stopBleScan() {
        try {
            ThingHomeSdk.getBleOperator().stopLeScan()
        } catch (_: Throwable) {}
        isTuyaScanning = false
        try {
            if (bleScanCallback != null && bleScanner != null) {
                val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                } else true
                if (hasScan) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            }
        } catch (_: Throwable) {}
        bleScanCallback = null
    }

    private fun stopDiscovery() {
        cancelAutoAdd()
        isAutoAddCancelled = true
        discoveryRunnable?.let { scanHandler.removeCallbacks(it) }
        discoveryRunnable = null
        stopBleScan()
        binding.radarScanView.stopScan()
    }

    private fun renderDiscoveredDevices() {
        updateConnectButtonLabel()
        if (discoveredDevices.isNotEmpty()) {
            binding.tvSearchingNearby.text = "Looking for nearby devices..."
            binding.tvAutoAddCountdown.visibility = View.VISIBLE

            // Polar orbit coordinates matching reference 25_Light_ad (angleDeg, radiusFraction)
            val orbitSlots = listOf(
                Pair(-126.0, 0.70f), // Top-left
                Pair(-40.0, 0.76f),  // Top-right
                Pair(166.0, 0.72f),  // Mid-left
                Pair(22.0, 0.76f),   // Mid-right
                Pair(90.0, 0.88f),   // Bottom-center
                Pair(-88.0, 0.90f)   // Top-center
            )

            val stageSizePx = (324 * resources.displayMetrics.density).toInt()
            val maxRadius = (stageSizePx / 2f) - (16 * resources.displayMetrics.density)

            val currentKeys = discoveredDevices.map { it.idKey }.toSet()
            val toRemove = renderedOrbitMap.keys.filter { it !in currentKeys }
            for (key in toRemove) {
                renderedOrbitMap[key]?.root?.let { binding.flOrbitDevicesOverlay.removeView(it) }
                renderedOrbitMap.remove(key)
            }

            discoveredDevices.forEachIndexed { index, dev ->
                val key = dev.idKey
                val existingBinding = renderedOrbitMap[key]

                val clickAction = View.OnClickListener {
                    cancelAutoAdd()
                    val freshest = discoveredDevices.firstOrNull { it.idKey == key } ?: dev
                    openDiscoveredDevice(freshest)
                }

                val slot = orbitSlots[index % orbitSlots.size]
                val rad = Math.toRadians(slot.first)
                val offsetX = (cos(rad) * maxRadius * slot.second).toInt()
                val offsetY = (sin(rad) * maxRadius * slot.second).toInt()

                if (existingBinding == null) {
                    val itemBinding = ItemOrbitDiscoveredDeviceBinding.inflate(layoutInflater, binding.flOrbitDevicesOverlay, false)
                    itemBinding.ivOrbitDeviceIcon.setImageResource(dev.iconRes)
                    itemBinding.tvOrbitDeviceName.text = dev.name.replace("\n", " ")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        itemBinding.flOrbitIconWrap.outlineSpotShadowColor = 0x260A192F
                        itemBinding.flOrbitIconWrap.outlineAmbientShadowColor = 0x100A192F
                    }

                    itemBinding.root.setOnClickListener(clickAction)
                    itemBinding.flOrbitIconWrap.setOnClickListener(clickAction)

                    val lp = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                    itemBinding.root.layoutParams = lp
                    itemBinding.root.translationX = offsetX.toFloat()
                    itemBinding.root.translationY = offsetY.toFloat()
                    itemBinding.root.scaleX = 0.4f
                    itemBinding.root.scaleY = 0.4f
                    itemBinding.root.alpha = 0f

                    binding.flOrbitDevicesOverlay.addView(itemBinding.root)
                    itemBinding.root.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(320L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                        .start()

                    renderedOrbitMap[key] = itemBinding
                } else {
                    existingBinding.ivOrbitDeviceIcon.setImageResource(dev.iconRes)
                    existingBinding.tvOrbitDeviceName.text = dev.name.replace("\n", " ")
                    existingBinding.root.translationX = offsetX.toFloat()
                    existingBinding.root.translationY = offsetY.toFloat()
                    existingBinding.root.setOnClickListener(clickAction)
                    existingBinding.flOrbitIconWrap.setOnClickListener(clickAction)
                }
            }

            startAutoAddCountdown()
        } else {
            renderedOrbitMap.clear()
            binding.flOrbitDevicesOverlay.removeAllViews()
            cancelAutoAdd()
            binding.tvAutoAddCountdown.visibility = View.GONE
            if (isNearbyTabSelected) {
                binding.radarScanView.startScan()
            }
            binding.tvSearchingNearby.text = "Looking for nearby devices..."
        }
    }

    private fun startAutoAddCountdown() {
        if (isPairingDialogOpen || isAutoAddCancelled || autoAddTimer != null) return
        remainingSeconds = 16
        updateCountdownText()

        binding.tvAutoAddCountdown.setOnClickListener {
            cancelAutoAdd()
            isAutoAddCancelled = true
            binding.tvAutoAddCountdown.text = "Devices will be added manually. Tap device to add."
        }

        autoAddTimer = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                if (isPairingDialogOpen || isAutoAddCancelled) {
                    cancelAutoAdd()
                    return
                }
                remainingSeconds--
                if (remainingSeconds > 0) {
                    updateCountdownText()
                    scanHandler.postDelayed(this, 1000L)
                } else {
                    cancelAutoAdd()
                    isAutoAddCancelled = true
                    if (!isPairingDialogOpen && discoveredDevices.size == 1) {
                        discoveredDevices.firstOrNull()?.let { dev ->
                            openDiscoveredDevice(dev)
                        }
                    } else if (discoveredDevices.size > 1) {
                        binding.tvAutoAddCountdown.text = "Multiple devices found. Tap a device above to connect."
                    }
                }
            }
        }
        scanHandler.postDelayed(autoAddTimer!!, 1000L)
    }

    private fun updateCountdownText() {
        val html = "Devices will be added automatically. <font color='#EF4444'><b>Cancel (${remainingSeconds})</b></font>"
        binding.tvAutoAddCountdown.text = androidx.core.text.HtmlCompat.fromHtml(
            html,
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun cancelAutoAdd() {
        autoAddTimer?.let { scanHandler.removeCallbacks(it) }
        autoAddTimer = null
    }

    private fun openDiscoveredDevice(dev: DiscoveredDevice) {
        cancelAutoAdd()
        isAutoAddCancelled = true
        if (isPairingDialogOpen) return
        openDiscoveredDevicePairingDialog(dev)
    }

    private fun openDiscoveredDevicePairingDialog(dev: DiscoveredDevice) {
        if (isPairingDialogOpen) return
        cancelAutoAdd()
        isAutoAddCancelled = true
        isPairingDialogOpen = true
        var launchedConnectingScreen = false

        currentPairingBottomSheet?.dismiss()
        val bottomSheet = BottomSheetDialog(this)
        currentPairingBottomSheet = bottomSheet
        val dialogBinding = DialogPairTuyaDeviceBinding.inflate(layoutInflater)
        bottomSheet.setContentView(dialogBinding.root)
        bottomSheet.setOnShowListener {
            bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialogBinding.ivDialogDeviceIcon.setImageResource(dev.iconRes)
        val cleanName = dev.name.replace("\n", " ")
        dialogBinding.tvDialogDeviceName.text = "Hubungkan $cleanName"

        val effectiveScanBean = cachedScanBeans[dev.idKey]
            ?: TuyaPairingRepository.scannedBeansByUuid[dev.idKey]
            ?: dev.scanBean

        val isSingleBleOnly = effectiveScanBean?.configType == BleConfigType.CONFIG_TYPE_SINGLE.type

        dialogBinding.layoutCameraQrCode.visibility = View.GONE
        dialogBinding.layoutPairingProgress.visibility = View.GONE
        if (isSingleBleOnly) {
            dialogBinding.layoutWifiInputs.visibility = View.GONE
            dialogBinding.tvDialogSubProtocol.text = "Tuya Smart Life • Bluetooth"
            dialogBinding.tvDialogInstruction.text = "Pastikan perangkat Bluetooth menyala dan berada di dekat ponsel."
        } else {
            dialogBinding.layoutWifiInputs.visibility = View.VISIBLE
            dialogBinding.tvDialogSubProtocol.text = "Tuya Smart Life • Bluetooth & Wi-Fi Dual-Mode"
            dialogBinding.tvDialogInstruction.text = "Pilih Wi-Fi 2.4 GHz dan masukkan kata sandi untuk menghubungkan perangkat."
        }
        dialogBinding.btnDialogStartPairing.text = "Konfirmasi"

        // Auto-detect or default Wi-Fi SSID
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val rawSsid = try { wifiManager?.connectionInfo?.ssid?.replace("\"", "") } catch (e: Exception) { null }
        val finalSsid = if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") rawSsid else "Nadhori Home"
        dialogBinding.etDialogSsid.setText(finalSsid)

        dialogBinding.btnDialogClose.setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheet.setOnDismissListener {
            isPairingDialogOpen = false
            currentPairingBottomSheet = null
            if (!launchedConnectingScreen && !isFinishing && !isDestroyed && !isTuyaScanning) {
                startRealBleScan()
            }
        }

        dialogBinding.btnDialogStartPairing.setOnClickListener {
            val ssid = dialogBinding.etDialogSsid.text?.toString()?.trim().orEmpty()
            val pass = dialogBinding.etDialogPassword.text?.toString()?.trim().orEmpty()

            if (!isSingleBleOnly && ssid.isEmpty()) {
                Toast.makeText(this, "Nama Wi-Fi (SSID) tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Close keyboard & bottom sheet immediately, then launch the full-screen Connecting Ring animation!
            hideKeyboard(dialogBinding.etDialogPassword)
            stopBleScan()
            launchedConnectingScreen = true
            bottomSheet.dismiss()

            val intent = ConnectingDeviceActivity.startWithCredentials(
                context = this,
                name = cleanName,
                iconRes = dev.iconRes,
                category = dev.category,
                subcategory = dev.subcategory,
                homeId = homeId,
                scanUuid = dev.idKey,
                ssid = ssid,
                pass = pass,
                isSingleBleOnly = isSingleBleOnly
            )
            connectingDeviceLauncher.launch(intent)
        }

        bottomSheet.show()
    }

    private fun openResetDeviceGuide(device: TuyaDevice) {
        val intent = ResetDeviceActivity.start(
            context = this,
            name = device.name,
            iconRes = device.iconRes,
            category = device.category,
            subcategory = device.subcategory,
            homeId = homeId,
            protocol = "Bluetooth"
        )
        resetDeviceLauncher.launch(intent)
    }

    private fun openPairingDialog(device: TuyaDevice) {
        val bottomSheet = BottomSheetDialog(this)
        val dialogBinding = DialogPairTuyaDeviceBinding.inflate(layoutInflater)
        bottomSheet.setContentView(dialogBinding.root)

        dialogBinding.ivDialogDeviceIcon.setImageResource(device.iconRes)
        dialogBinding.tvDialogDeviceName.text = "Hubungkan ${device.name.replace("\n", " ")}"

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
                    val assignedName = if (!devResp.name.isNullOrBlank() && !devResp.name.equals("null", true)) devResp.name else device.name
                    val newDev = TuyaDeviceRepository.addCustomDevice(
                        name = assignedName,
                        category = device.category,
                        productId = if (!devResp.productId.isNullOrBlank()) devResp.productId else "tt_plug_v1",
                        explicitDevId = devResp.devId
                    )
                    dialogBinding.layoutPairingProgress.visibility = View.GONE
                    dialogBinding.btnDialogStartPairing.isEnabled = true
                    dialogBinding.btnDialogStartPairing.text = "Mulai Hubungkan"
                    bottomSheet.dismiss()
                    Toast.makeText(this, "Perangkat '${device.name.replace("\n", " ")}' berhasil terhubung!", Toast.LENGTH_LONG).show()
                    TuyaDeviceClassifier.openDeviceControl(
                        context = this,
                        devId = newDev.devId,
                        name = newDev.name,
                        category = newDev.category,
                        productId = newDev.productId,
                        isOnline = true,
                        isSwitchOn = true,
                        isFromPairing = true
                    )
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
