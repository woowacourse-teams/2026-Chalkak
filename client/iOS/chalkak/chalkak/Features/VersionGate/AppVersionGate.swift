import Foundation
import Observation

struct AppVersion: Equatable {
    let major: Int
    let minor: Int
    let patch: Int

    init(major: Int, minor: Int, patch: Int) {
        self.major = major
        self.minor = minor
        self.patch = patch
    }

    init?(_ value: String) {
        let components = value.split(separator: ".", omittingEmptySubsequences: false)
        let numbers = components.compactMap { Int($0) }
        guard components.allSatisfy({ !$0.isEmpty && $0.allSatisfy({ $0.isASCII && $0.isNumber }) }),
              (2...3).contains(components.count),
              numbers.count == components.count,
              numbers.allSatisfy({ $0 >= 0 })
        else { return nil }

        major = numbers[0]
        minor = numbers[1]
        patch = numbers.count == 3 ? numbers[2] : 0
    }

    func isOlder(than minimumVersion: AppVersion) -> Bool {
        if major != minimumVersion.major { return major < minimumVersion.major }
        if minor != minimumVersion.minor { return minor < minimumVersion.minor }
        return patch < minimumVersion.patch
    }

    static func current(in bundle: Bundle = .main) -> AppVersion? {
        guard let value = bundle.object(
            forInfoDictionaryKey: "CFBundleShortVersionString"
        ) as? String else { return nil }

        return AppVersion(value)
    }

    static func currentString(in bundle: Bundle = .main) -> String {
        bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
            ?? "-"
    }
}

enum AppUpdateCheckResult: Equatable {
    case upToDate
    case updateRequired(storeURL: URL)
    case unavailable
}

struct AppVersionChecker {
    private let session: URLSession
    private let baseURL: URL?
    private let currentVersion: AppVersion?

    // Keep this public request separate from authenticated clients and shared caches.
    private static let publicSession: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.urlCredentialStorage = nil
        return URLSession(configuration: configuration)
    }()

    init(
        session: URLSession? = nil,
        baseURL: URL? = AppConfiguration().apiBaseURL,
        currentVersion: AppVersion? = AppVersion.current()
    ) {
        self.session = session ?? Self.publicSession
        self.baseURL = baseURL
        self.currentVersion = currentVersion
    }

    func check() async -> AppUpdateCheckResult {
        guard let request = makeRequest(), let currentVersion else { return .unavailable }

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200
            else { return .unavailable }

            let policy = try JSONDecoder().decode(AppVersionResponse.self, from: data)
            guard policy.platform == "IOS" else { return .unavailable }
            if policy.minimumSupportedVersion == nil && policy.storeUrl == nil {
                return .upToDate
            }
            guard let minimum = policy.minimumSupportedVersion.flatMap(AppVersion.init)
            else { return .unavailable }
            guard currentVersion.isOlder(than: minimum) else { return .upToDate }
            guard let storeURL = policy.storeUrl.flatMap(URL.init(string:)),
                  storeURL.scheme?.lowercased() == "https",
                  storeURL.host?.isEmpty == false
            else { return .unavailable }
            return .updateRequired(storeURL: storeURL)
        } catch {
            return .unavailable
        }
    }

    func makeRequest() -> URLRequest? {
        guard let baseURL,
              baseURL.scheme?.lowercased() == "https", baseURL.host?.isEmpty == false,
              var components = URLComponents(
                  url: baseURL.appendingPathComponent("app-version"),
                  resolvingAgainstBaseURL: false
              )
        else { return nil }
        components.queryItems = [URLQueryItem(name: "platform", value: "IOS")]
        guard let url = components.url else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 3
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.httpShouldHandleCookies = false
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("no-cache, no-store", forHTTPHeaderField: "Cache-Control")
        return request
    }
}

@MainActor
@Observable
final class AppVersionGateViewModel {
    typealias UpdateChecker = @MainActor @Sendable () async -> AppUpdateCheckResult

    private(set) var requiredUpdateStoreURL: URL?

    private let updateChecker: UpdateChecker
    private var isChecking = false

    init(
        updateChecker: @escaping UpdateChecker = {
            await AppVersionChecker().check()
        }
    ) {
        self.updateChecker = updateChecker
    }

    func checkForUpdate() async {
        guard !isChecking else { return }
        isChecking = true
        defer { isChecking = false }

        switch await updateChecker() {
        case .upToDate:
            requiredUpdateStoreURL = nil
        case let .updateRequired(storeURL):
            requiredUpdateStoreURL = storeURL
        case .unavailable:
            // 최초 조회 실패는 앱을 허용하고, 이미 확인된 강제 업데이트 상태는 유지한다.
            break
        }
    }
}

private struct AppVersionResponse: Decodable {
    let platform: String
    let minimumSupportedVersion: String?
    let storeUrl: String?
}
