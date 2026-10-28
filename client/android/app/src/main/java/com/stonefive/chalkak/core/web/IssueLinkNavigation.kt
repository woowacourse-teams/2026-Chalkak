package com.stonefive.chalkak.core.web

import java.net.URI

internal enum class IssueLinkNavigation {
    Allow,
    Block,
    OpenExternally,
}

internal fun issueLinkNavigation(
    currentUrl: String?,
    targetUrl: String,
    isMainFrame: Boolean,
    hasGesture: Boolean,
): IssueLinkNavigation {
    if (!isMainFrame) return IssueLinkNavigation.Allow
    val target = runCatching { URI(targetUrl) }.getOrNull() ?: return IssueLinkNavigation.Block
    if (target.scheme?.lowercase() !in setOf("http", "https") || target.host.isNullOrBlank()) {
        return IssueLinkNavigation.Block
    }
    if (
        (target.rawFragment != null || currentUrl?.contains('#') == true) &&
        targetUrl.substringBefore('#') == currentUrl?.substringBefore('#')
    ) {
        return IssueLinkNavigation.Allow
    }
    return if (hasGesture) IssueLinkNavigation.OpenExternally else IssueLinkNavigation.Block
}
