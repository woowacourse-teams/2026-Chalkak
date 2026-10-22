package com.stonefive.chalkak.core.web

import org.junit.Assert.assertEquals
import org.junit.Test

class IssueLinkNavigationTest {
    private val page = "https://example.com/issue?id=1"

    @Test
    fun `clicked links open externally including same host and same URL`() {
        listOf("https://other.com/article", "http://other.com/article", "https://example.com/next", page).forEach {
            assertEquals(IssueLinkNavigation.OpenExternally, navigate(it))
        }
    }

    @Test
    fun `same document anchors stay inside the issue`() {
        assertEquals(IssueLinkNavigation.Allow, navigate("$page#section"))
        assertEquals(
            IssueLinkNavigation.Allow,
            issueLinkNavigation("$page#one", "$page#two", true, true),
        )
        assertEquals(IssueLinkNavigation.Allow, issueLinkNavigation("$page#one", page, true, true))
        assertEquals(IssueLinkNavigation.OpenExternally, navigate("https://example.com/next#section"))
    }

    @Test
    fun `automatic navigation cannot launch the browser`() {
        assertEquals(IssueLinkNavigation.Block, issueLinkNavigation(page, "https://other.com", true, false))
    }

    @Test
    fun `non web and malformed destinations are blocked`() {
        listOf("intent://app", "javascript:alert(1)", "file:///etc/hosts", "mailto:a@b.com", "https:", "bad url")
            .forEach { assertEquals(IssueLinkNavigation.Block, navigate(it)) }
    }

    @Test
    fun `subframe navigation stays inside without launching the browser`() {
        assertEquals(IssueLinkNavigation.Allow, issueLinkNavigation(page, "https://other.com", false, true))
    }

    @Test
    fun `user initiated new window opens its destination externally`() {
        assertEquals(
            IssueLinkNavigation.OpenExternally,
            issueLinkNavigation(null, "https://other.com", true, true),
        )
    }

    private fun navigate(target: String): IssueLinkNavigation = issueLinkNavigation(page, target, true, true)
}
