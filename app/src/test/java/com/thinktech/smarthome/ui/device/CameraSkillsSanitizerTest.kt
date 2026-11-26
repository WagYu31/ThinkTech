package com.thinktech.smarthome.ui.device

import com.thingclips.smart.android.camera.sdk.constant.ThingIPCConstant
import com.thingclips.smart.camera.ipccamerasdk.utils.CameraConstant
import com.thinktech.smarthome.data.TuyaDeviceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CameraSkillsSanitizerTest {

    @Test
    fun `null or empty skills default to SDK_PROVIDER_V2 integer`() {
        val fromNull = CameraControlActivity.sanitizeSkillsP2pType(null, hasTutkSdk = false)
        assertTrue(fromNull["p2pType"] is Int)
        assertEquals(CameraConstant.SDK_PROVIDER_V2, fromNull["p2pType"])

        val fromMissingKey = CameraControlActivity.sanitizeSkillsP2pType(mapOf("cloudStorage" to 1), hasTutkSdk = false)
        assertTrue(fromMissingKey["p2pType"] is Int)
        assertEquals(CameraConstant.SDK_PROVIDER_V2, fromMissingKey["p2pType"])
        assertEquals(1, fromMissingKey["cloudStorage"])
    }

    @Test
    fun `BigDecimal Long and String p2pType values normalize to Integer`() {
        val fromBigDecimal = CameraControlActivity.sanitizeSkillsP2pType(
            mapOf("p2pType" to BigDecimal("4")),
            hasTutkSdk = false
        )
        assertTrue(fromBigDecimal["p2pType"] is Int)
        assertEquals(4, fromBigDecimal["p2pType"])

        val fromLong = CameraControlActivity.sanitizeSkillsP2pType(
            mapOf("p2pType" to 2L),
            hasTutkSdk = false
        )
        assertEquals(2, fromLong["p2pType"])

        val fromString = CameraControlActivity.sanitizeSkillsP2pType(
            mapOf("p2pType" to "8"),
            hasTutkSdk = false
        )
        assertEquals(8, fromString["p2pType"])
    }

    @Test
    fun `TUTK provider 1 maps to V2 when optional TUTK SDK is absent`() {
        val withoutTutk = CameraControlActivity.sanitizeSkillsP2pType(
            mapOf("p2pType" to 1),
            hasTutkSdk = false
        )
        assertEquals(CameraConstant.SDK_PROVIDER_V2, withoutTutk["p2pType"])

        val withTutk = CameraControlActivity.sanitizeSkillsP2pType(
            mapOf("p2pType" to 1),
            hasTutkSdk = true
        )
        assertEquals(1, withTutk["p2pType"])
    }

    @Test
    fun `initial clarity and bidirectional clarity fallback resolve cleanly`() {
        assertEquals(
            ThingIPCConstant.DEFINITION_SD,
            CameraControlActivity.resolveInitialClarity(ThingIPCConstant.DEFINITION_SD)
        )
        assertEquals(
            ThingIPCConstant.DEFINITION_HD,
            CameraControlActivity.resolveInitialClarity(ThingIPCConstant.DEFINITION_HD)
        )
        assertEquals(
            ThingIPCConstant.DEFINITION_HD,
            CameraControlActivity.resolveInitialClarity(null)
        )
        assertEquals(
            ThingIPCConstant.DEFINITION_HD,
            CameraControlActivity.resolveInitialClarity(0)
        )

        assertEquals(
            ThingIPCConstant.DEFINITION_SD,
            CameraControlActivity.fallbackClarity(ThingIPCConstant.DEFINITION_HD)
        )
        assertEquals(
            ThingIPCConstant.DEFINITION_HD,
            CameraControlActivity.fallbackClarity(ThingIPCConstant.DEFINITION_SD)
        )
    }

    @Test
    fun `strict privacy mode ignores unrelated DP 105 when basic_private is absent from schema`() {
        val dpsWithUnrelated105 = mapOf<String, Any?>("105" to true, "101" to true)
        val schemaWithoutPrivacy = mapOf("basic_indicator" to "101", "basic_osd" to "105")
        assertEquals(
            null,
            CameraControlActivity.resolveStrictPrivacyMode(dpsWithUnrelated105, schemaWithoutPrivacy)
        )
        assertEquals(
            null,
            CameraControlActivity.resolveStrictPrivacyMode(dpsWithUnrelated105, emptyMap())
        )

        val schemaWithPrivacy = mapOf("basic_private" to "105")
        assertEquals(
            true,
            CameraControlActivity.resolveStrictPrivacyMode(mapOf("105" to true), schemaWithPrivacy)
        )
        assertEquals(
            false,
            CameraControlActivity.resolveStrictPrivacyMode(mapOf("105" to "false"), schemaWithPrivacy)
        )
    }

    @Test
    fun `stale custom devices unbound from cloud are pruned while recent pairings and demos stay`() {
        val now = 100_000L
        val pruned = TuyaDeviceRepository.staleCustomIdsToPrune(
            customIds = listOf("a3494ef9_unbound", "new_pair_123", "already_in_cloud", "demo_cam_1"),
            cloudSdkIds = setOf("already_in_cloud"),
            pairedTimestampsMs = mapOf("new_pair_123" to (now - 10_000L), "a3494ef9_unbound" to (now - 90_000L)),
            nowMs = now,
            graceMs = 45_000L
        )
        assertTrue("a3494ef9_unbound" in pruned)
        assertTrue("already_in_cloud" in pruned)
        assertFalse("new_pair_123" in pruned)
        assertFalse("demo_cam_1" in pruned)
    }
}

