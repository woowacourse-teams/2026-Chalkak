import Foundation

struct SettingsViewState: Equatable, Sendable {
    var isLoading = true
    var isLoggedIn = false
    var isAccountActionInProgress = false
    var signatureURL: URL?
    var version = "-"
    var accountDialog: SettingsAccountDialog?
}

enum SettingsEvent: Equatable, Sendable {
    case showMessage(String)
    case signedOut
}
