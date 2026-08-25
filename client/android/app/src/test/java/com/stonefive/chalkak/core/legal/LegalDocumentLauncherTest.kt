package com.stonefive.chalkak.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegalDocumentLauncherTest {
    @Test
    fun `display failure invokes failure callback`() {
        var failureCount = 0
        val launcher = LegalDocumentLauncher(
            showLegalDocument = { error("display failed") },
            onOpenFailed = { failureCount++ },
        )

        assertFalse(launcher.open(LegalDocument.PRIVACY_POLICY))

        assertEquals(1, failureCount)
    }
}
