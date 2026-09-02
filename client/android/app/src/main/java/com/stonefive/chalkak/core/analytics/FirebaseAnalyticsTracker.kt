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

    private companion object {
        const val BOTTOM_NAVIGATION_SELECTED_EVENT = "bottom_navigation_selected"
        const val DESTINATION_PARAM = "destination"
    }
}
