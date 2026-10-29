import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct AppVersionGateTests {
    @Test
    func parsesNumericVersionAndTwoComponentProjectVersion() throws {
        #expect(try #require(AppVersion("2.10.3")) == AppVersion(major: 2, minor: 10, patch: 3))
        #expect(try #require(AppVersion("1.0")) == AppVersion(major: 1, minor: 0, patch: 0))
    }

    @Test(arguments: ["1", "1.2.3.4", "1.two.3", "1.2.-1", "1.+2.3", "1..3"])
    func rejectsInvalidVersion(_ value: String) {
        #expect(AppVersion(value) == nil)
    }

    @Test(arguments: [
        (current: "1.2.9", minimum: "2.0.0"),
        (current: "1.2.9", minimum: "1.3.0"),
        (current: "1.2.9", minimum: "1.2.10"),
        (current: "1.9.0", minimum: "1.10.0"),
    ])
    func requiresUpdateBelowMinimumIncludingPatch(current: String, minimum: String) throws {
        let currentVersion = try #require(AppVersion(current))
        #expect(currentVersion.isOlder(than: try #require(AppVersion(minimum))))
    }

    @Test(arguments: [
        (current: "1.2.9", minimum: "1.2.9"),
        (current: "1.3.0", minimum: "1.2.9"),
        (current: "2.0.0", minimum: "1.9.9"),
        (current: "1.0", minimum: "1.0.0"),
    ])
    func allowsSupportedVersions(current: String, minimum: String) throws {
        let currentVersion = try #require(AppVersion(current))
        #expect(!currentVersion.isOlder(than: try #require(AppVersion(minimum))))
    }

    @Test
    func publicRequestUsesIOSAndBypassesCacheWithoutCredentials() throws {
        let request = try #require(AppVersionChecker(
            baseURL: URL(string: "https://example.com/api/v1/"),
            currentVersion: AppVersion("1.0")
        ).makeRequest())
        #expect(request.url?.absoluteString == "https://example.com/api/v1/app-version?platform=IOS")
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
        #expect(request.cachePolicy == .reloadIgnoringLocalAndRemoteCacheData)
        #expect(request.value(forHTTPHeaderField: "Cache-Control") == "no-cache, no-store")
        #expect(!request.httpShouldHandleCookies)
    }

    @Test
    func decodesRemoteMinimumAndStoreURL() async {
        let result = await check(body: #"{"platform":"IOS","minimumSupportedVersion":"1.0.1","storeUrl":"https://apps.apple.com/app/id123456789"}"#)
        #expect(result == .updateRequired(storeURL: URL(string: "https://apps.apple.com/app/id123456789")!))
    }

    @Test(arguments: [
        #"{"platform":"IOS","minimumSupportedVersion":null,"storeUrl":null}"#,
        #"{"platform":"IOS","minimumSupportedVersion":"1.0.0","storeUrl":"https://apps.apple.com/app/id123456789"}"#,
        #"{"platform":"IOS","minimumSupportedVersion":"0.9.0","storeUrl":"https://apps.apple.com/app/id123456789"}"#,
    ])
    func unsetOrSupportedPolicyAllowsAccess(_ body: String) async {
        #expect(await check(body: body) == .upToDate)
    }

    @Test(arguments: [
        #"{"platform":"ANDROID","minimumSupportedVersion":null,"storeUrl":null}"#,
        #"{"platform":"IOS","minimumSupportedVersion":"invalid","storeUrl":"https://apps.apple.com/app/id1"}"#,
        #"{"platform":"IOS","minimumSupportedVersion":"2.0.0","storeUrl":null}"#,
        #"{"platform":"IOS","minimumSupportedVersion":"2.0.0","storeUrl":"javascript:alert(1)"}"#,
        #"{"platform":"IOS","minimumSupportedVersion":null,"storeUrl":"https://apps.apple.com/app/id1"}"#,
        #"{}"#,
    ])
    func malformedPolicyIsUnavailable(_ body: String) async {
        #expect(await check(body: body) == .unavailable)
    }

    @Test(arguments: [400, 401, 403, 500])
    func nonSuccessResponseIsUnavailable(_ status: Int) async {
        #expect(await check(body: "{}", status: status) == .unavailable)
    }

    @Test
    func unavailableRecheckPreservesRequiredUpdateAndUnsetClearsIt() async {
        let storeURL = URL(string: "https://apps.apple.com/app/id123456789")!
        var results: [AppUpdateCheckResult] = [.updateRequired(storeURL: storeURL), .unavailable, .upToDate]
        let viewModel = AppVersionGateViewModel { results.removeFirst() }
        await viewModel.checkForUpdate()
        await viewModel.checkForUpdate()
        #expect(viewModel.requiredUpdateStoreURL == storeURL)
        await viewModel.checkForUpdate()
        #expect(viewModel.requiredUpdateStoreURL == nil)
    }

    private func check(body: String, status: Int = 200) async -> AppUpdateCheckResult {
        AppVersionURLProtocol.handler = { request in
            (HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!, Data(body.utf8))
        }
        defer { AppVersionURLProtocol.handler = nil }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AppVersionURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        return await AppVersionChecker(
            session: session,
            baseURL: URL(string: "https://example.com/api/v1/"),
            currentVersion: AppVersion("1.0.0")
        ).check()
    }
}

private final class AppVersionURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var handler: (@Sendable (URLRequest) -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let (response, data) = Self.handler?(request) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
