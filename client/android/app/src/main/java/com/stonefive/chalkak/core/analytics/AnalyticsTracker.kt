package com.stonefive.chalkak.core.analytics

interface AnalyticsTracker {
    fun trackScreenView(
        screenName: String,
        screenClass: String,
    )

    fun trackBottomNavigationSelection(destination: String)

    fun trackHomeIssueLayoutExposed(isIssueOnTop: Boolean)

    fun trackHomeInteraction(interaction: HomeInteraction)
}
