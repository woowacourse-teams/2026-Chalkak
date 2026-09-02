import Foundation

struct AppConfiguration {
    let apiBaseURL: URL?
    let googleClientID: String?
    let googleServerClientID: String?
    let googleReversedClientID: String?
    let kakaoNativeAppKey: String?

    init(bundle: Bundle = .main) {
        apiBaseURL = bundle.configuredURL(forKey: "API_BASE_URL")
        googleClientID = bundle.configuredValue(forKey: "GOOGLE_IOS_CLIENT_ID")
        googleServerClientID = bundle.configuredValue(forKey: "GOOGLE_SERVER_CLIENT_ID")
        googleReversedClientID = bundle.configuredValue(forKey: "GOOGLE_REVERSED_CLIENT_ID")
        kakaoNativeAppKey = bundle.configuredValue(forKey: "KAKAO_NATIVE_APP_KEY")
    }
}

private extension Bundle {
    func configuredValue(forKey key: String) -> String? {
        guard let value = object(forInfoDictionaryKey: key) as? String else {
            return nil
        }

        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedValue.isEmpty, !trimmedValue.hasPrefix("$(") else {
            return nil
        }
        return trimmedValue
    }

    func configuredURL(forKey key: String) -> URL? {
        guard let value = configuredValue(forKey: key), let url = URL(string: value) else {
            return nil
        }
        return url
    }
}
