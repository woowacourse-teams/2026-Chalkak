import Foundation
import Testing
@testable import chalkak

@MainActor
struct AppVersionGateTests {
    @Test
    func parsesSemanticVersion() throws {
        let version = try #require(AppVersion("2.10.3"))

        #expect(version == AppVersion(major: 2, minor: 10, patch: 3))
    }

    @Test
    func acceptsCurrentTwoComponentProjectVersion() throws {
        let version = try #require(AppVersion("1.0"))

        #expect(version == AppVersion(major: 1, minor: 0, patch: 0))
    }

    @Test(arguments: ["1", "1.2.3.4", "1.two.3", "1.2.-1"])
    func rejectsInvalidVersion(_ value: String) {
        #expect(AppVersion(value) == nil)
    }

    @Test(arguments: [
        (current: "1.2.9", store: "2.0.0"),
        (current: "1.2.9", store: "1.3.0"),
    ])
    func requiresUpdateWhenStoreMajorOrMinorIsHigher(
        current: String,
        store: String
    ) throws {
        let currentVersion = try #require(AppVersion(current))
        let storeVersion = try #require(AppVersion(store))

        #expect(currentVersion.requiresMajorOrMinorUpdate(to: storeVersion))
    }

    @Test(arguments: [
        (current: "1.2.0", store: "1.2.9"),
        (current: "1.3.0", store: "1.2.9"),
        (current: "2.0.0", store: "1.9.9"),
    ])
    func doesNotRequireUpdateForPatchOnlyOrOlderStoreVersion(
        current: String,
        store: String
    ) throws {
        let currentVersion = try #require(AppVersion(current))
        let storeVersion = try #require(AppVersion(store))

        #expect(!currentVersion.requiresMajorOrMinorUpdate(to: storeVersion))
    }

    @Test
    func usesAppStoreStorefrontCountryForLookup() throws {
        let countryCode = try #require(
            AppStoreVersionChecker.lookupCountryCode(from: "USA")
        )
        let url = try #require(
            AppStoreVersionChecker.lookupURL(
                bundleIdentifier: "com.stonefive.chalkak",
                countryCode: countryCode
            )
        )
        let components = try #require(
            URLComponents(url: url, resolvingAgainstBaseURL: false)
        )
        let queryItems = try #require(components.queryItems)

        #expect(countryCode == "US")
        #expect(queryItems.contains(URLQueryItem(name: "country", value: "us")))
    }

    @Test
    func unavailableRecheckPreservesRequiredUpdate() async {
        let storeURL = URL(string: "https://apps.apple.com/app/id123456789")!
        var results: [AppUpdateCheckResult] = [
            .updateRequired(storeURL: storeURL),
            .unavailable,
        ]
        let viewModel = AppVersionGateViewModel {
            results.removeFirst()
        }

        await viewModel.checkForUpdate()
        await viewModel.checkForUpdate()

        #expect(viewModel.requiredUpdateStoreURL == storeURL)
    }
}
