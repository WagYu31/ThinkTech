package com.thinktech.smarthome

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.thingclips.imagepipeline.okhttp3.OkHttpImagePipelineConfigFactory
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.optimus.sdk.ThingOptimusSdk
import com.thinktech.smarthome.data.AppLanguageManager
import com.thinktech.smarthome.ui.settings.AppLanguageActivity
import okhttp3.OkHttpClient

class ThinkTechApp : Application() {

    companion object {
        private const val TAG = "ThinkTechApp"
        lateinit var instance: ThinkTechApp
            private set
        var isSdkInitialized: Boolean = false
            private set
        @Volatile
        private var initializedP2pUid: String = ""

        // shortcut: invokes IThingP2P.init(uid) via reflection because bare ThingHomeSdk.init skips ModuleApp login events, switch to ThingWrapper if BizBundle UI modules are added
        fun ensureP2pUserSessionInitialized(): Boolean {
            if (!isSdkInitialized) return false
            return runCatching {
                val p2p = ThingIPCSdk::class.java.getMethod("getP2P").invoke(null) ?: return false
                val uid = runCatching { ThingHomeSdk.getUserInstance()?.user?.uid }
                    .getOrNull()
                    .orEmpty()
                if (uid.isNotBlank() && uid != initializedP2pUid) {
                    p2p.javaClass.getMethod("init", String::class.java).invoke(p2p, uid)
                    initializedP2pUid = uid
                    Log.i(TAG, "ThingP2P user session & MQTT 302 initialized for uid=${uid.take(6)}***")
                }
                true
            }.getOrElse {
                Log.w(TAG, "ensureP2pUserSessionInitialized warning: ${it.message}")
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLanguageManager.applySavedLocale(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (activity is AppLanguageActivity) return
                AppLanguageManager.localizeActivity(activity)
                activity.window?.decorView?.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        AppLanguageManager.localizeActivity(activity)
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Global crash handler to catch and log any unexpected exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()
                getSharedPreferences("thinktech_crash_log", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", stackTrace)
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (t: Throwable) {
                // Ignore secondary error
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Inisialisasi Fresco untuk DecryptImageView pada ThingCameraView IPC SDK
            if (!Fresco.hasBeenInitialized()) {
                runCatching {
                    val pipelineConfig = OkHttpImagePipelineConfigFactory
                        .newBuilder(this, OkHttpClient())
                        .build()
                    Fresco.initialize(this, pipelineConfig)
                }.onFailure {
                    Fresco.initialize(this)
                }
            }

            // Inisialisasi ThingHomeSdk & Optimus (Tuya Smart Life App SDK v7.8.0)
            ThingHomeSdk.init(this)
            ThingHomeSdk.setDebugMode(true)
            runCatching { ThingOptimusSdk.init(this) }
            val ipcReady = runCatching {
                ThingIPCSdk.getCameraInstance()?.apply { setLogEnabled(true) } != null
            }.getOrDefault(false)
            // Preload native ThingP2P & MQTT 302 signaling listener (setara ThingP2PLoadManager)
            val p2pReady = runCatching {
                ThingIPCSdk::class.java.getMethod("getP2P").invoke(null) != null
            }.getOrDefault(false)
            isSdkInitialized = true
            ensureP2pUserSessionInitialized()
            Log.i(TAG, "Tuya Smart Life & IPC SDK initialized (ipc=$ipcReady, p2p=$p2pReady).")
        } catch (e: Throwable) {
            Log.w(TAG, "Tuya SDK initialization deferred or requires security assets: ${e.message}")
            isSdkInitialized = false
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            if (isSdkInitialized) {
                ThingHomeSdk.onDestroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying Tuya SDK: ${e.message}")
        }
    }
}
