package com.stonefive.chalkak.core.legal

class LegalDocumentLauncher(
    private val showLegalDocument: (LegalDocument) -> Unit,
    private val onOpenFailed: () -> Unit = {},
) {
    fun open(document: LegalDocument): Boolean = try {
        showLegalDocument(document)
        true
    } catch (_: IllegalStateException) {
        onOpenFailed()
        false
    }
}
