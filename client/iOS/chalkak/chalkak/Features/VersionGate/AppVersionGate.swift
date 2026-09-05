import Foundation
import Observation
import StoreKit

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
        guard (2...3).contains(components.count),
              numbers.count == components.count,
              numbers.allSatisfy({ $0 >= 0 })
        else { return nil }

        major = numbers[0]
        minor = numbers[1]
        patch = numbers.count == 3 ? numbers[2] : 0
    }

    func requiresMajorOrMinorUpdate(to storeVersion: AppVersion) -> Bool {
        storeVersion.major > major ||
            (storeVersion.major == major && storeVersion.minor > minor)
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

struct AppStoreVersionChecker {
    private let session: URLSession
    private let bundle: Bundle

    init(
        session: URLSession = .shared,
        bundle: Bundle = .main
    ) {
        self.session = session
        self.bundle = bundle
    }

    func check() async -> AppUpdateCheckResult {
        guard let countryCode = await Self.currentLookupCountryCode(),
              let bundleIdentifier = bundle.bundleIdentifier,
              let currentVersion = AppVersion.current(in: bundle),
              let requestURL = Self.lookupURL(
                  bundleIdentifier: bundleIdentifier,
                  countryCode: countryCode
              )
        else { return .unavailable }

        var request = URLRequest(url: requestURL)
        request.timeoutInterval = 3
        request.cachePolicy = .useProtocolCachePolicy

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200..<300).contains(httpResponse.statusCode)
            else { return .unavailable }

            let lookup = try JSONDecoder().decode(AppStoreLookupResponse.self, from: data)
            guard let product = lookup.results.first(where: {
                $0.bundleId == bundleIdentifier
            }),
            let storeVersion = AppVersion(product.version),
            let storeURL = URL(string: product.trackViewUrl)
            else { return .unavailable }

            return currentVersion.requiresMajorOrMinorUpdate(to: storeVersion)
                ? .updateRequired(storeURL: storeURL)
                : .upToDate
        } catch {
            return .unavailable
        }
    }

    static func lookupCountryCode(from storefrontCountryCode: String) -> String? {
        let region = Locale(identifier: "und_\(storefrontCountryCode)").region?.identifier
        guard let region, region.count == 2 else { return nil }
        return region
    }

    static func lookupURL(bundleIdentifier: String, countryCode: String) -> URL? {
        var components = URLComponents(string: "https://itunes.apple.com/lookup")
        components?.queryItems = [
            URLQueryItem(name: "bundleId", value: bundleIdentifier),
            URLQueryItem(name: "country", value: countryCode.lowercased()),
            URLQueryItem(name: "entity", value: "software"),
        ]
        return components?.url
    }

    private static func currentLookupCountryCode() async -> String? {
        guard let storefront = await Storefront.current else { return nil }
        return lookupCountryCode(from: storefront.countryCode)
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
            await AppStoreVersionChecker().check()
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

private struct AppStoreLookupResponse: Decodable {
    let results: [AppStoreProduct]
}

private struct AppStoreProduct: Decodable {
    let bundleId: String
    let version: String
    let trackViewUrl: String
}
