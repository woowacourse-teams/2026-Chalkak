import FirebaseAnalytics

protocol AnalyticsTracking {
    func trackScreenView(screenName: String, screenClass: String)
    func trackBottomNavigationSelection(destination: String)
}

struct FirebaseAnalyticsTracker: AnalyticsTracking {
    func trackScreenView(screenName: String, screenClass: String) {
        Analytics.logEvent(
            AnalyticsEventScreenView,
            parameters: [
                AnalyticsParameterScreenName: screenName,
                AnalyticsParameterScreenClass: screenClass
            ]
        )
    }

    func trackBottomNavigationSelection(destination: String) {
        Analytics.logEvent(
            "bottom_navigation_selected",
            parameters: ["destination": destination]
        )
    }
}
