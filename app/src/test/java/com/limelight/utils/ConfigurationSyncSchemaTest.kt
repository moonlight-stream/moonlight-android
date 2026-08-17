package com.limelight.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationSyncSchemaTest {
    @Test
    fun screenDs5TouchpadPreferenceIsPortable() {
        assertTrue(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                PreferenceConfiguration.SCREEN_DS5_TOUCHPAD_PREF_STRING
            )
        )
    }

    @Test
    fun schemaV1FixtureHasExpectedSectionsAndTypedValues() {
        validateSchemaFixture(
            readFixture("config-sync/schema-v1.example.json").asJsonObject,
            expectedSchemaVersion = 1,
            expectSyncMetadata = false
        )
    }

    @Test
    fun schemaV2FixtureHasSyncMetadata() {
        validateSchemaFixture(
            readFixture("config-sync/schema-v2.example.json").asJsonObject,
            expectedSchemaVersion = 2,
            expectSyncMetadata = true
        )
    }

    private fun validateSchemaFixture(
        root: JsonObject,
        expectedSchemaVersion: Int,
        expectSyncMetadata: Boolean
    ) {
        assertEquals(expectedSchemaVersion, root["schemaVersion"].asInt)
        if (expectSyncMetadata) {
            assertTrue(root["syncId"].asString.isNotBlank())
            assertTrue(root["deviceId"].asString.isNotBlank())
            assertTrue(root["backupDeviceKey"].asString.isNotBlank())
            assertEquals("sameDevice", root["backupTarget"].asString)
        }
        assertTrue(root["packageName"].asString.isNotBlank())
        assertTrue(root["appVersionCode"].asLong > 0)
        assertTrue(root["appVersionName"].asString.isNotBlank())
        assertTrue(root["exportedAt"].asLong > 0)

        val sections = root["sections"].asJsonObject
        for (section in preferenceSections) {
            assertNotNull("Missing section $section", sections[section])
            validatePreferenceSection(section, sections[section].asJsonObject, expectSyncMetadata)
        }

        val crownProfiles = sections["crownProfiles"].asJsonArray
        assertEquals(1, crownProfiles.size())
        validateCrownProfile(crownProfiles[0].asJsonObject, expectSyncMetadata)

        if (expectSyncMetadata) {
            validatePairingSection(sections["pairing"].asJsonObject)
        }
    }

    @Test
    fun localSnapshotMetadataIsNotPortableDefaultPreference() {
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_AUTO_SNAPSHOT_ENABLED
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_LOCAL_SNAPSHOT_AT
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_BACKGROUND_SYNC_ENABLED
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_ENABLED
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_EXTERNAL_SNAPSHOT_IMPORT
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_EXTERNAL_SYNC_TREE_URI
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_EXTERNAL_SYNC_DIRECTORY
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_BACKUP_PASSWORD
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_EXTERNAL_CONTENT_HASH
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_APPLIED
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_AT
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_ERROR
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_READ_EXTERNAL
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_SUCCESS
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_BACKGROUND_SYNC_WROTE_EXTERNAL
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_EXTERNAL_SYNC_AT
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LAST_EXTERNAL_SYNC_ID
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_LOCAL_SNAPSHOT_NOW
            )
        )
        assertFalse(
            ConfigurationSyncManager.isPortableDefaultPreferenceKey(
                ConfigurationSyncManager.PREF_SYNC_STATUS
            )
        )
    }

    @Test
    fun portableSharedPreferenceChangeDetectionCoversSyncedStores() {
        val syncedStores = ConfigurationSyncManager.portableSharedPreferenceNames()
        assertTrue("app_last_settings should be observed", "app_last_settings" in syncedStores)
        assertTrue("custom_resolutions should be observed", "custom_resolutions" in syncedStores)
        assertTrue("SceneConfigs should be observed", "SceneConfigs" in syncedStores)
        assertTrue("HiddenApps should be observed", "HiddenApps" in syncedStores)
        assertTrue("AppView should be observed", "AppView" in syncedStores)

        assertTrue(ConfigurationSyncManager.isPortableSharedPreferenceKey("app_last_settings", "host-app"))
        assertTrue(ConfigurationSyncManager.isPortableSharedPreferenceKey("custom_resolutions", "custom_resolutions"))
        assertTrue(ConfigurationSyncManager.isPortableSharedPreferenceKey("SceneConfigs", "scene_1"))
        assertTrue(ConfigurationSyncManager.isPortableSharedPreferenceKey("HiddenApps", "host_uuid"))
        assertTrue(ConfigurationSyncManager.isPortableSharedPreferenceKey("AppView", "app_background_mode"))

        assertFalse(ConfigurationSyncManager.isPortableSharedPreferenceKey("AppView", "display_selection"))
        assertFalse(ConfigurationSyncManager.isPortableSharedPreferenceKey("unknown", "app_background_mode"))
        assertFalse(ConfigurationSyncManager.isPortableSharedPreferenceKey("custom_resolutions", null))
    }

    private fun readFixture(path: String): JsonElement {
        val stream = javaClass.classLoader!!.getResourceAsStream(path)
            ?: error("Missing fixture: $path")
        return stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
    }

    private fun validatePreferenceSection(
        sectionName: String,
        section: JsonObject,
        expectSyncMetadata: Boolean
    ) {
        val values = section["values"]?.asJsonObject
        assertNotNull("$sectionName.values is missing", values)
        assertTrue("$sectionName.values should not be empty", values!!.entrySet().isNotEmpty())

        for ((key, encodedValue) in values.entrySet()) {
            assertTrue("Empty key in $sectionName", key.isNotBlank())
            validateTypedValue("$sectionName.$key", encodedValue.asJsonObject, expectSyncMetadata)
        }
    }

    private fun validateTypedValue(path: String, encodedValue: JsonObject, expectSyncMetadata: Boolean) {
        val type = encodedValue["type"]?.asString
        assertTrue("$path has unsupported type $type", type in supportedTypes)
        assertTrue("$path.value is missing", encodedValue.has("value"))
        if (expectSyncMetadata) {
            assertTrue("$path.updatedAt is missing", encodedValue.has("updatedAt"))
            assertTrue("$path.updatedAt must be positive", encodedValue["updatedAt"].asLong > 0L)
            assertTrue("$path.updatedBy is missing", encodedValue.has("updatedBy"))
            assertTrue("$path.updatedBy must not be blank", encodedValue["updatedBy"].asString.isNotBlank())
        }

        val value = encodedValue["value"]
        when (type) {
            "string" -> assertTrue("$path.value must be string", value.isJsonPrimitive && value.asJsonPrimitive.isString)
            "boolean" -> assertTrue("$path.value must be boolean", value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
            "int" -> {
                assertTrue("$path.value must be int", value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                val intValue = value.asLong
                assertTrue("$path.value is outside int range", intValue in Int.MIN_VALUE..Int.MAX_VALUE)
            }
            "long" -> assertTrue("$path.value must be long", value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            "float" -> assertTrue("$path.value must be float", value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            "stringSet" -> validateStringSet("$path.value", value.asJsonArray)
            "deleted" -> assertTrue("$path.value must be null", value.isJsonNull)
        }
    }

    private fun validateStringSet(path: String, array: JsonArray) {
        for (item in array) {
            assertTrue("$path item must be string", item.isJsonPrimitive && item.asJsonPrimitive.isString)
        }
    }

    private fun validateCrownProfile(profile: JsonObject, expectSyncMetadata: Boolean) {
        assertTrue(profile["sourceConfigId"].asLong > 0)
        assertTrue(profile["name"].asString.isNotBlank())
        assertTrue(profile["payload"].asString.isNotBlank())
        if (expectSyncMetadata) {
            assertTrue(profile["profileId"].asString.isNotBlank())
            assertTrue(profile["updatedAt"].asLong > 0L)
            assertTrue(profile["updatedBy"].asString.isNotBlank())
            assertTrue(profile["deleted"].asBoolean == false)
        }
    }

    private fun validatePairingSection(pairing: JsonObject) {
        assertTrue(pairing["updatedAt"].asLong > 0L)
        assertEquals(16, pairing["uniqueId"].asString.length)
        assertTrue(pairing["clientCertificatePem"].asString.isNotBlank())
        assertTrue(pairing["clientPrivateKeyPkcs8"].asString.isNotBlank())

        val computers = pairing["computers"].asJsonArray
        assertEquals(1, computers.size())
        val computer = computers[0].asJsonObject
        assertTrue(computer["uuid"].asString.isNotBlank())
        assertTrue(computer["name"].asString.isNotBlank())
        assertTrue(computer["serverCertificateDer"].asString.isNotBlank())
        assertTrue(computer["httpsPort"].asInt > 0)
        validateAddress("pairing.computers[0].localAddress", computer["localAddress"].asJsonObject)
        validateAddress("pairing.computers[0].activeAddress", computer["activeAddress"].asJsonObject)
    }

    private fun validateAddress(path: String, address: JsonObject) {
        assertTrue("$path.address is missing", address["address"].asString.isNotBlank())
        assertTrue("$path.port must be positive", address["port"].asInt > 0)
    }

    private companion object {
        val preferenceSections = listOf(
            "defaultPreferences",
            "appLastSettings",
            "customResolutions",
            "sceneConfigs",
            "appViewPreferences",
            "hiddenApps"
        )

        val supportedTypes = setOf(
            "string",
            "boolean",
            "int",
            "long",
            "float",
            "stringSet",
            "deleted"
        )
    }
}
