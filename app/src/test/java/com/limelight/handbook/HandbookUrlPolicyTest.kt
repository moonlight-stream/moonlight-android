package com.limelight.handbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandbookUrlPolicyTest {
    @Test
    fun acceptsOnlyExactHttpsHandbookOrigins() {
        assertNotNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/docs/"))
        assertNotNull(HandbookUrlPolicy.parse("https://www.alkaidlab.cn/docs/guide/start.html"))

        assertNull(HandbookUrlPolicy.parse("http://www.alkaidlab.com/docs/"))
        assertNull(HandbookUrlPolicy.parse("https://alkaidlab.com/docs/"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com.evil.example/docs/"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com./docs/"))
        assertNull(HandbookUrlPolicy.parse("https://user@www.alkaidlab.com/docs/"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com:8443/docs/"))
    }

    @Test
    fun rejectsPathsOutsideDocsRootAndEncodedTraversal() {
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/docs"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/documentation/"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/docs-outside/"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/docs/%2e%2e/private"))
        assertNull(HandbookUrlPolicy.parse("https://www.alkaidlab.com/docs%2Fprivate"))
    }

    @Test
    fun preservesPageComponentsAndAlwaysUsesInternationalFirst() {
        val page = HandbookUrlPolicy.parse(
            "https://www.alkaidlab.cn/docs/guide.html?mode=compact#controls"
        )
        assertNotNull(page)

        val candidates = HandbookUrlPolicy.originCandidates(page!!)
        assertEquals(2, candidates.size)
        assertEquals("www.alkaidlab.com", candidates[0].host)
        assertEquals("www.alkaidlab.cn", candidates[1].host)
        assertEquals("/docs/guide.html", candidates[0].encodedPath)
        assertEquals("mode=compact", candidates[0].encodedQuery)
        assertNull(candidates[0].encodedFragment)
    }

    @Test
    fun recognizesOnlySafeExternalBrowserTargets() {
        assertTrue(HandbookUrlPolicy.isExternalHttps("https://example.org/article"))
        assertTrue(HandbookUrlPolicy.isExternalHttps("https://www.alkaidlab.com/"))

        assertFalse(HandbookUrlPolicy.isExternalHttps("https://www.alkaidlab.com/docs/article"))
        assertFalse(HandbookUrlPolicy.isExternalHttps("http://example.org/article"))
        assertFalse(HandbookUrlPolicy.isExternalHttps("intent://example.org/article"))
        assertFalse(HandbookUrlPolicy.isExternalHttps("https://user@example.org/article"))
    }
}
