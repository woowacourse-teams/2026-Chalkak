package com.stonefive.chalkak.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentLauncherTest {
    @Test
    fun `법률 문서 enum은 제공된 Notion URL을 매핑한다`() {
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
    fun `법률 문서를 열 때 정확한 문서를 표시하도록 요청한다`() {
        var launchedDocument: LegalDocument? = null
        val launcher = LegalDocumentLauncher(showLegalDocument = { launchedDocument = it })

        val opened = launcher.open(LegalDocument.PRIVACY_POLICY)

        assertTrue(opened)
        assertEquals(LegalDocument.PRIVACY_POLICY, launchedDocument)
    }

    @Test
    fun `허용되지 않은 문서 URL이면 실패 콜백을 호출하고 false를 반환한다`() {
        var failureCalled = false
        val launcher = LegalDocumentLauncher(
            showLegalDocument = {},
            onOpenFailed = { failureCalled = true },
        )

        val opened = launcher.open("http://app.notion.com/legal")

        assertFalse(opened)
        assertTrue(failureCalled)
    }
}
