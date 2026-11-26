# ---------------------------------
# Tuya / ThingClips ProGuard Rules
# ---------------------------------

# FastJson (Diperlukan oleh Tuya SDK untuk parsing data)
-keep class com.alibaba.fastjson.** { *; }
-dontwarn com.alibaba.fastjson.**

# MQTT Client (Komunikasi real-time perangkat)
-keep class com.thingclips.smart.mqttclient.mqttv3.** { *; }
-dontwarn com.thingclips.smart.mqttclient.mqttv3.**

# OkHttp & Okio (Jaringan)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Core Tuya / ThingClips Classes
-keep class com.thingclips.** { *; }
-dontwarn com.thingclips.**
