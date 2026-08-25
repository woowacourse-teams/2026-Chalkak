package com.stonefive.chalkak.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentLauncherTest {
    @Test
    fun `legal documents map to the provided Notion URLs`() {
        assertEquals(
            "https://app.notion.com/p/3b56b8e8e36780af8ec8ea0bf92b97a9?source=copy_link",
            LegalDocument.PRIVACY_POLICY.url,
        )
        assertEquals(
            "https://app.notion.com/p/3c66b8e8e3678064b543c26b5c0f932d?source=copy_link",
            LegalDocument.TERMS_OF_SERVICE.url,
        )
    }

    @Test
    fun `opening a legal document requests the expected document`() {
        var launchedDocument: LegalDocument? = null
        val launcher = LegalDocumentLauncher(showLegalDocument = { launchedDocument = it })

        val opened = launcher.open(LegalDocument.PRIVACY_POLICY)

        assertTrue(opened)
        assertEquals(LegalDocument.PRIVACY_POLICY, launchedDocument)
    }

    @Test
    fun `display failure invokes failure callback and returns false`() {
        var failureCalled = false
        val launcher = LegalDocumentLauncher(
            showLegalDocument = { error("display failed") },
            onOpenFailed = { failureCalled = true },
        )

        val opened = launcher.open(LegalDocument.PRIVACY_POLICY)

        assertFalse(opened)
        assertTrue(failureCalled)
    }
}
