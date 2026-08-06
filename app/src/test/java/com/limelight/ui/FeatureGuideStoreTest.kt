package com.limelight.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGuideStoreTest {
    @Test
    fun completedRevisionStaysHidden() {
        val preferences = FakePreferences()
        val store = FeatureGuideStore(preferences)
        val guide = FeatureGuideSpec("settings_discovery", revision = 1)

        assertTrue(store.shouldShow(guide))
        store.markCompleted(guide)
        assertFalse(store.shouldShow(guide))
    }

    @Test
    fun newRevisionIsEligibleAfterPreviousRevisionWasCompleted() {
        val store = FeatureGuideStore(FakePreferences())
        val firstRevision = FeatureGuideSpec("settings_discovery", revision = 1)
        val secondRevision = firstRevision.copy(revision = 2)

        store.markCompleted(firstRevision)

        assertFalse(store.shouldShow(firstRevision))
        assertTrue(store.shouldShow(secondRevision))
    }

    @Test
    fun leavingGuideIncompleteKeepsMaybeLaterEligible() {
        val store = FeatureGuideStore(FakePreferences())
        val guide = FeatureGuideSpec("settings_discovery", revision = 1)

        // Snoozing intentionally performs no persistent completion write.
        assertTrue(store.shouldShow(guide))
    }

    @Test
    fun registryUsesUniqueVersionedKeys() {
        val keys = listOf(
            FeatureGuideRegistry.PcViewDiscovery,
            FeatureGuideRegistry.AppViewDiscovery,
            FeatureGuideRegistry.GameMenuDiscovery
        ).map { it.completionKey }

        assertNotEquals(keys[0], keys[1])
        assertNotEquals(keys[1], keys[2])
        assertNotEquals(keys[0], keys[2])
    }

    private class FakePreferences : FeatureGuidePreferences {
        private val values = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String): Boolean = values[key] ?: false

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
