package com.thinktech.smarthome.data.model

data class SmartDeviceItem(
    var devId: String = "",
    var name: String = "",
    var iconUrl: String? = null,
    var isOnline: Boolean = true,
    var isSwitchOn: Boolean = false,
    var productId: String = "",
    var category: String = "",
    var room: String = "Ruang Tamu"
) {
    // Explicit no-argument constructor for FastJSON serialization / deserialization
    constructor() : this("", "", null, true, false, "", "", "Ruang Tamu")
}
