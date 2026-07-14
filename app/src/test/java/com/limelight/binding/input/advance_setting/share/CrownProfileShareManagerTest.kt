package com.limelight.binding.input.advance_setting.share

import com.limelight.utils.MathUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownProfileShareManagerTest {
    @Test
    fun createBundleWrapsOnlyPublicCrownProfileData() {
        val payload = validPayload()

        val bundle = CrownProfileShareManager.createBundle(
            profileName = "Apex Layout",
            payload = payload,
            metadata = CrownProfileShareManager.ExportMetadata(
                packageName = "com.limelight.test",
                appVersionCode = 390,
                appVersionName = "12.9.8",
                layoutBasis = CrownProfileShareManager.LayoutBasis(
                    widthPx = 2400,
                    heightPx = 1080,
                    densityDpi = 440,
                    density = 2.75f,
                    orientation = "landscape"
                ),
                exportedAtMillis = 1781712000000L
            )
        )

        val root = JSONObject(bundle)
        assertEquals(CrownProfileShareManager.BUNDLE_KIND, root.getString("kind"))
        assertEquals(CrownProfileShareManager.SCHEMA_VERSION, root.getInt("schemaVersion"))
        assertEquals("Apex Layout", root.getString("name"))
        assertEquals(payload, root.getJSONObject("profile").getString("payload"))
        assertTrue(root.getJSONObject("profile").getString("profileId").startsWith("public-payload-"))
        assertEquals(2400, root.getJSONObject("layoutBasis").getInt("widthPx"))
        assertEquals(1080, root.getJSONObject("layoutBasis").getInt("heightPx"))
        assertEquals(440, root.getJSONObject("layoutBasis").getInt("densityDpi"))
        assertEquals("landscape", root.getJSONObject("layoutBasis").getString("orientation"))
        assertFalse(bundle.contains("deviceId"))
        assertFalse(bundle.contains("backupDeviceKey"))
        assertFalse(bundle.contains("pairing"))
        assertFalse(bundle.contains("clientPrivateKey"))
    }

    @Test
    fun parseImportTextAcceptsBundle() {
        val payload = validPayload(elementCount = 2)
        val bundle = CrownProfileShareManager.createBundle(
            profileName = "My Layout",
            payload = payload,
            metadata = CrownProfileShareManager.ExportMetadata(
                packageName = "com.limelight.test",
                appVersionCode = 390,
                appVersionName = "12.9.8"
            )
        )

        val imported = CrownProfileShareManager.parseImportText(bundle)

        assertEquals("My Layout", imported.name)
        assertEquals("My Layout", imported.installName)
        assertEquals(payload, imported.payload)
        assertEquals(9, imported.payloadInfo.version)
        assertEquals(2, imported.payloadInfo.elementCount)
        assertEquals(2, imported.payloadInfo.settingsCount)
    }

    @Test
    fun bundleRoundTripPreservesWheelAlignmentExtraAttribute() {
        val extraAttributes = JSONObject()
            .put("segmentAlignment", "edge")
            .toString()
        val elements = JSONArray()
            .put(
                JSONObject()
                    .put("element_id", 1)
                    .put("element_type", 10)
                    .put("extra_attributes", extraAttributes)
            )
            .toString()
        val payload = validPayloadForElements(elements)
        val bundle = CrownProfileShareManager.createBundle(
            profileName = "Corner Wheel",
            payload = payload,
            metadata = CrownProfileShareManager.ExportMetadata(
                packageName = "com.limelight.test",
                appVersionCode = 392,
                appVersionName = "12.10.4"
            )
        )

        val imported = CrownProfileShareManager.parseImportText(bundle)
        val importedElements = JSONArray(JSONObject(imported.payload).getString("elements"))
        val importedExtraAttributes = JSONObject(
            importedElements.getJSONObject(0).getString("extra_attributes")
        )

        assertEquals("edge", importedExtraAttributes.getString("segmentAlignment"))
    }

    @Test
    fun payloadForInstallAsNewUsesBundleNameAsConfigName() {
        val bundle = CrownProfileShareManager.createBundle(
            profileName = "Store Layout",
            payload = validPayload(),
            metadata = CrownProfileShareManager.ExportMetadata(
                packageName = "com.limelight.test",
                appVersionCode = 390,
                appVersionName = "12.9.8"
            )
        )
        val imported = CrownProfileShareManager.parseImportText(bundle)

        val payload = CrownProfileShareManager.payloadForInstallAsNew(imported)

        val root = JSONObject(payload)
        val settings = JSONObject(root.getString("settings"))
        assertEquals("Store Layout", settings.getString("config_name"))
        assertEquals(
            MathUtils.computeMD5("${root.getInt("version")}${root.getString("settings")}${root.getString("elements")}"),
            root.getString("md5")
        )
        CrownProfileShareManager.validatePayload(payload)
    }

    @Test
    fun payloadForInstallAsNewKeepsLegacyPayloadName() {
        val payload = validPayload()
        val imported = CrownProfileShareManager.parseImportText(payload)

        val installPayload = CrownProfileShareManager.payloadForInstallAsNew(imported)

        assertEquals(payload, installPayload)
    }

    @Test
    fun parseImportTextPreservesLayoutBasis() {
        val bundle = CrownProfileShareManager.createBundle(
            profileName = "Tablet Layout",
            payload = validPayload(),
            metadata = CrownProfileShareManager.ExportMetadata(
                packageName = "com.limelight.test",
                appVersionCode = 390,
                appVersionName = "12.9.8",
                layoutBasis = CrownProfileShareManager.LayoutBasis(
                    widthPx = 2560,
                    heightPx = 1600,
                    densityDpi = 320,
                    density = 2f,
                    orientation = "landscape"
                )
            )
        )

        val imported = CrownProfileShareManager.parseImportText(bundle)

        assertEquals(2560, imported.layoutBasis?.widthPx)
        assertEquals(1600, imported.layoutBasis?.heightPx)
        assertEquals(320, imported.layoutBasis?.densityDpi)
        assertEquals(2f, imported.layoutBasis?.density)
        assertEquals("landscape", imported.layoutBasis?.orientation)
    }

    @Test
    fun parseImportTextAcceptsLegacyMdatPayload() {
        val payload = validPayload()

        val imported = CrownProfileShareManager.parseImportText(payload)

        assertEquals("Legacy .mdat", imported.sourceLabel)
        assertEquals(payload, imported.payload)
        assertEquals(9, imported.payloadInfo.version)
    }

    @Test(expected = CrownProfileShareManager.CrownProfileShareException::class)
    fun parseImportTextRejectsTamperedBundleHash() {
        val payload = validPayload()
        val bundle = JSONObject(
            CrownProfileShareManager.createBundle(
                profileName = "My Layout",
                payload = payload,
                metadata = CrownProfileShareManager.ExportMetadata(
                    packageName = "com.limelight.test",
                    appVersionCode = 390,
                    appVersionName = "12.9.8"
                )
            )
        )
        bundle.getJSONObject("profile").put("payloadSha256", "bad")

        CrownProfileShareManager.parseImportText(bundle.toString())
    }

    @Test(expected = CrownProfileShareManager.CrownProfileShareException::class)
    fun validatePayloadRejectsTamperedMdatChecksum() {
        val payload = JSONObject(validPayload())
        payload.put("settings", """{"config_name":"changed"}""")

        CrownProfileShareManager.validatePayload(payload.toString())
    }

    @Test
    fun parseStoreIndexAcceptsProfiles() {
        val index = JSONObject()
            .put("kind", CrownProfileShareManager.INDEX_KIND)
            .put("schemaVersion", CrownProfileShareManager.SCHEMA_VERSION)
            .put(
                "profiles",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("bundleId", "crown.apex.fps")
                            .put("name", "Apex FPS Layout")
                            .put("summary", "Fast access abilities and movement")
                            .put("author", "WA Crown")
                            .put("game", "Apex Legends")
                            .put("tags", JSONArray().put("fps").put("movement"))
                            .put(
                                "layoutBasis",
                                JSONObject()
                                    .put("widthPx", 2400)
                                    .put("heightPx", 1080)
                                    .put("densityDpi", 440)
                                    .put("density", 2.75)
                                    .put("orientation", "landscape")
                            )
                            .put("updatedAt", "2026-06-18T00:00:00Z")
                            .put("url", "profiles/apex/fps.crown.json")
                    )
            )

        val profiles = CrownProfileShareManager.parseStoreIndex(index.toString())

        assertEquals(1, profiles.size)
        assertEquals("crown.apex.fps", profiles[0].bundleId)
        assertEquals("Apex FPS Layout", profiles[0].name)
        assertEquals("WA Crown", profiles[0].author)
        assertEquals("Apex Legends", profiles[0].game)
        assertEquals(listOf("fps", "movement"), profiles[0].tags)
        assertEquals(2400, profiles[0].layoutBasis?.widthPx)
        assertEquals(1080, profiles[0].layoutBasis?.heightPx)
        assertEquals(440, profiles[0].layoutBasis?.densityDpi)
        assertEquals("landscape", profiles[0].layoutBasis?.orientation)
        assertEquals("profiles/apex/fps.crown.json", profiles[0].url)
    }

    @Test
    fun resolveStoreProfileUrlAcceptsRelativeUrls() {
        val resolved = CrownProfileShareManager.resolveStoreProfileUrl(
            indexUrl = "https://raw.githubusercontent.com/qiin2333/crown-profiles/main/index/v1.json",
            profileUrl = "../profiles/apex/fps.crown.json"
        )

        assertEquals(
            "https://raw.githubusercontent.com/qiin2333/crown-profiles/main/profiles/apex/fps.crown.json",
            resolved
        )
    }

    @Test(expected = CrownProfileShareManager.CrownProfileShareException::class)
    fun resolveStoreProfileUrlRejectsHttpUrls() {
        CrownProfileShareManager.resolveStoreProfileUrl(
            indexUrl = "https://raw.githubusercontent.com/qiin2333/crown-profiles/main/index/v1.json",
            profileUrl = "http://example.com/profiles/apex/fps.crown.json"
        )
    }

    @Test(expected = CrownProfileShareManager.CrownProfileShareException::class)
    fun parseStoreIndexRejectsWrongKind() {
        val index = JSONObject()
            .put("kind", CrownProfileShareManager.BUNDLE_KIND)
            .put("schemaVersion", CrownProfileShareManager.SCHEMA_VERSION)
            .put("profiles", JSONArray())

        CrownProfileShareManager.parseStoreIndex(index.toString())
    }

    private fun validPayload(elementCount: Int = 1): String {
        val elements = buildString {
            append("[")
            repeat(elementCount) { index ->
                if (index > 0) append(",")
                append("""{"element_id":$index,"element_type":1}""")
            }
            append("]")
        }
        return validPayloadForElements(elements)
    }

    private fun validPayloadForElements(elements: String): String {
        val settings = """{"config_name":"default","touch_enable":"true"}"""
        val version = 9
        val md5 = MathUtils.computeMD5("$version$settings$elements")
        return JSONObject()
            .put("version", version)
            .put("settings", settings)
            .put("elements", elements)
            .put("md5", md5)
            .toString()
    }
}
