package com.stonefive.chalkak.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsTracker(context: Context) : AnalyticsTracker {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun trackScreenView(
        screenName: String,
        screenClass: String,
    ) {
        firebaseAnalytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            },
        )
    }

    override fun trackBottomNavigationSelection(destination: String) {
        firebaseAnalytics.logEvent(
            BOTTOM_NAVIGATION_SELECTED_EVENT,
            Bundle().apply {
                putString(DESTINATION_PARAM, destination)
            },
        )
    }

    override fun trackHomeIssueLayoutExposed(isIssueOnTop: Boolean) {
        firebaseAnalytics.setUserProperty(
            HOME_ISSUE_LAYOUT_PROPERTY,
            if (isIssueOnTop) HOME_ISSUE_LAYOUT_TOP else HOME_ISSUE_LAYOUT_BOTTOM,
        )
        firebaseAnalytics.logEvent(HOME_ISSUE_LAYOUT_EXPOSED_EVENT, null)
    }

    override fun trackHomeInteraction(interaction: HomeInteraction) {
        firebaseAnalytics.logEvent(
            interaction.eventName,
            Bundle().apply {
                interaction.parameters.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putLong(key, value.toLong())
                    }
                }
            },
        )
    }

    private companion object {
        const val BOTTOM_NAVIGATION_SELECTED_EVENT = "bottom_navigation_selected"
        const val DESTINATION_PARAM = "destination"
        const val HOME_ISSUE_LAYOUT_EXPOSED_EVENT = "home_issue_layout_exposed"
        const val HOME_ISSUE_LAYOUT_PROPERTY = "home_issue_layout"
        const val HOME_ISSUE_LAYOUT_TOP = "top"
        const val HOME_ISSUE_LAYOUT_BOTTOM = "bottom"
    }
}
