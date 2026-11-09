import FirebaseAnalytics

struct AnalyticsScreen: Equatable {
    let name: String
    let screenClass: String
}

protocol AnalyticsTracking {
    func trackScreenView(screenName: String, screenClass: String)
    func trackBottomNavigationSelection(destination: String)
    func trackHomeIssueLayoutExposed(isIssueOnTop: Bool)
    func trackHomeInteraction(_ interaction: HomeInteraction)
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

    func trackHomeIssueLayoutExposed(isIssueOnTop: Bool) {
        Analytics.setUserProperty(isIssueOnTop ? "top" : "bottom", forName: "home_issue_layout")
        Analytics.logEvent("home_issue_layout_exposed", parameters: nil)
    }

    func trackHomeInteraction(_ interaction: HomeInteraction) {
        Analytics.logEvent(interaction.eventName, parameters: interaction.parameters)
    }
}
