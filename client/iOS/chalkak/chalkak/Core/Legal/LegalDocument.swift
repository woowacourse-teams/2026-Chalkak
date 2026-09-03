import Foundation

enum LegalDocument: String, Identifiable {
    case privacyPolicy
    case termsOfService

    var id: Self { self }

    var url: URL {
        switch self {
        case .privacyPolicy:
            URL(string: "https://app.notion.com/p/3b56b8e8e36780af8ec8ea0bf92b97a9?source=copy_link")!
        case .termsOfService:
            URL(string: "https://app.notion.com/p/3c66b8e8e3678064b543c26b5c0f932d?source=copy_link")!
        }
    }
}

enum LegalDocumentNavigationPolicy {
    private static let allowedHosts = ["notion.com", "notion.so", "notion.site"]

    static func allows(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https",
              let host = url.host?.lowercased()
        else { return false }

        return allowedHosts.contains { host == $0 || host.hasSuffix(".\($0)") }
    }
}

