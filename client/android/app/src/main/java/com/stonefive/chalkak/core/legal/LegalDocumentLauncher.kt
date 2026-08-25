package com.stonefive.chalkak.core.legal

import java.net.URI

/** Validates a legal document URL and asks the UI to display it. */
class LegalDocumentLauncher(
    private val showLegalDocument: (LegalDocument) -> Unit,
    private val onOpenFailed: () -> Unit = {},
) {
    /** Returns true when the document display was requested. */
    fun open(document: LegalDocument): Boolean {
        if (!document.url.isAllowedLegalDocumentUrl()) {
            onOpenFailed()
            return false
        }

        return try {
            showLegalDocument(document)
            true
        } catch (_: IllegalStateException) {
            onOpenFailed()
            false
        }
    }

    internal fun open(url: String): Boolean {
        val document = LegalDocument.entries.firstOrNull { it.url == url }
        return document?.let(::open) ?: run {
            onOpenFailed()
            false
        }
    }
}

private fun String.isAllowedLegalDocumentUrl(): Boolean = runCatching { URI(this) }
    .getOrNull()
    ?.isAllowedLegalDocumentUri() == true

private fun URI.isAllowedLegalDocumentUri(): Boolean = scheme == HTTPS_SCHEME &&
    host == NOTION_HOST

private const val HTTPS_SCHEME = "https"
private const val NOTION_HOST = "app.notion.com"
