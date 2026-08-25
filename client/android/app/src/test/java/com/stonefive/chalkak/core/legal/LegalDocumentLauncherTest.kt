package com.stonefive.chalkak.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegalDocumentLauncherTest {
    @Test
    fun `invalid legal document uri invokes failure callback without launching activity`() {
        var launchCount = 0
        var failureCount = 0
        val launcher = LegalDocumentLauncher(
            showLegalDocument = { launchCount++ },
            onOpenFailed = { failureCount++ },
        )

        assertFalse(launcher.open("http://app.notion.com/legal"))
        assertFalse(launcher.open("https://app.notion.com.evil.example/legal"))

        assertEquals(2, failureCount)
        assertEquals(0, launchCount)
    }
}
