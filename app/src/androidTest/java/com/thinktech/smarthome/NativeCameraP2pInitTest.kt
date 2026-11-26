package com.thinktech.smarthome

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thingclips.smart.camera.ipccamerasdk.utils.CameraConstant
import com.thingclips.smart.camera.middleware.p2p.ThingSmartCameraP2P
import com.thingclips.smart.camera.middleware.p2p.ThingSmartCameraP2PSync
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCameraP2pInitTest {

    @Test
    fun nativeCameraP2pEngineLoadsAllSharedLibrariesAndCreatesCameraInstance() {
        val p2p = ThingSmartCameraP2P(CameraConstant.SDK_PROVIDER_V2, "a39fba28_verify")
        try {
            val field = ThingSmartCameraP2PSync::class.java.getDeclaredField("mCameraP2P").apply {
                isAccessible = true
            }
            val nativeCamera = field.get(p2p)
            assertNotNull("Expected mCameraP2P (IPCThingP2PCamera) to be non-null on arm64", nativeCamera)
        } finally {
            runCatching { p2p.destroyP2P() }
        }
    }
}

