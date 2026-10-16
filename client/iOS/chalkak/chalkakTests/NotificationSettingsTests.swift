import Foundation
import Testing
import UserNotifications
@testable import chalkak

struct NotificationPermissionPromptActionTests {
    @Test("미결정 사용자는 앱 안내 없이 시스템 권한창만 표시한다")
    func requestsOnlySystemPermissionBeforeFirstDecision() {
        #expect(NotificationPermissionPromptAction.resolve(
            status: .notDetermined, hasShownPrompt: false
        ) == .requestSystemPermission)
    }

    @Test("이미 허용하거나 거부한 사용자는 앱 안내를 표시한다")
    func showsAppPromptForExistingDecisions() {
        for status in [UNAuthorizationStatus.authorized, .denied, .provisional, .ephemeral] {
            #expect(NotificationPermissionPromptAction.resolve(
                status: status, hasShownPrompt: false
            ) == .showAppPrompt)
        }
    }

    @Test("한 번 안내한 뒤에는 권한 상태와 관계없이 다시 표시하지 않는다")
    func doesNotRepeatPrompt() {
        for status in [UNAuthorizationStatus.notDetermined, .authorized, .denied, .provisional, .ephemeral] {
            #expect(NotificationPermissionPromptAction.resolve(
                status: status, hasShownPrompt: true
            ) == .none)
        }
    }
}

struct NotificationTogglePermissionActionTests {
    @Test("OS에서 차단한 상태에서 켤 때만 시스템 설정으로 이동한다")
    func opensSettingsOnlyWhenEnablingDeniedNotifications() {
        #expect(NotificationTogglePermissionAction.shouldOpenSettings(enabled: true, status: .denied))
        for status in [UNAuthorizationStatus.notDetermined, .authorized, .provisional, .ephemeral] {
            #expect(!NotificationTogglePermissionAction.shouldOpenSettings(enabled: true, status: status))
        }
        for status in [UNAuthorizationStatus.denied, .notDetermined, .authorized, .provisional, .ephemeral] {
            #expect(!NotificationTogglePermissionAction.shouldOpenSettings(enabled: false, status: status))
        }
    }
}

@MainActor
struct NotificationSettingsViewModelTests {
    @Test("최초 안내의 허용·거부는 두 설정을 한 요청으로 저장한다")
    func savesInitialChoiceForBothCategories() async {
        for enabled in [true, false] {
            var saved: (Bool?, Bool?)?
            var saves = 0
            let model = NotificationSettingsViewModel(
                saveSettings: { topic, moderation in
                    saved = (topic, moderation)
                    saves += 1
                }
            )
            let succeeded = await model.setAllPushEnabled(enabled)
            #expect(succeeded)
            #expect(saves == 1)
            #expect(saved?.0 == enabled)
            #expect(saved?.1 == enabled)
            #expect(model.viewState.settings == .init(
                topicPushEnabled: enabled, moderationPushEnabled: enabled
            ))
        }
    }

    @Test("최초 안내 선택 저장 실패는 성공으로 처리하지 않는다")
    func initialChoiceReportsSaveFailure() async {
        let model = NotificationSettingsViewModel(
            saveSettings: { _, _ in throw SettingsAPIError.network }
        )
        let succeeded = await model.setAllPushEnabled(false)
        #expect(!succeeded)
        #expect(model.viewState.settings == nil)
        #expect(model.saveErrorMessage != nil)
    }

    @Test("오늘 주제 알림 변경은 주제 설정만 부분 수정한다")
    func updatesTopicPreferenceOnly() async {
        var saved: (Bool?, Bool?)?
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { topic, moderation in saved = (topic, moderation) }
        )
        await model.setTopicPushEnabled(false)
        #expect(saved?.0 == false)
        #expect(saved?.1 == nil)
        #expect(model.viewState.settings == .init(topicPushEnabled: false, moderationPushEnabled: true))
    }

    @Test("승인·반려 알림 변경은 검수 설정만 부분 수정한다")
    func updatesModerationPreferenceOnly() async {
        var saved: (Bool?, Bool?)?
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { topic, moderation in saved = (topic, moderation) }
        )
        await model.setModerationPushEnabled(false)
        #expect(saved?.0 == nil)
        #expect(saved?.1 == false)
        #expect(model.viewState.settings == .init(topicPushEnabled: true, moderationPushEnabled: false))
    }

    @Test("저장 도중 계정이 바뀌면 이전 계정 설정을 적용하지 않는다")
    func ignoresSaveAfterAccountReset() async {
        let gate = AsyncGate()
        var saves = 0
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: false, moderationPushEnabled: false)),
            saveSettings: { _, _ in saves += 1; await gate.wait() }
        )
        let task = Task { await model.setTopicPushEnabled(true) }
        while !model.viewState.isSaving { await Task.yield() }
        model.reset()
        await gate.open()
        await task.value
        #expect(saves == 1)
        #expect(model.viewState.settings == nil)
    }

    @Test("같은 항목 저장 중 중복 요청은 한 번만 전송된다")
    func preventsDuplicateSaves() async {
        let gate = AsyncGate()
        var saveCount = 0
        let viewModel = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { _, _ in
                saveCount += 1
                await gate.wait()
            }
        )
        let first = Task { await viewModel.setTopicPushEnabled(false) }
        while !viewModel.viewState.isSaving { await Task.yield() }
        await viewModel.setTopicPushEnabled(false)
        await gate.open()
        await first.value
        #expect(saveCount == 1)
    }

    @Test("주제 저장 중 검수 토글은 비활성화되지 않고 독립적으로 저장된다")
    func keepsOtherToggleEnabledDuringTopicSave() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { topic, _ in
                if topic != nil { await gate.wait() }
            }
        )
        let first = Task { await model.setTopicPushEnabled(false) }
        while !model.viewState.isSavingTopic { await Task.yield() }
        #expect(model.viewState.isTopicDisabled)
        #expect(!model.viewState.isModerationDisabled)
        await model.setModerationPushEnabled(false)
        await gate.open()
        await first.value
        #expect(model.viewState.settings == .init(topicPushEnabled: false, moderationPushEnabled: false))
        #expect(!model.viewState.isSaving)
    }

    @Test("검수 저장 중 주제 토글은 비활성화되지 않는다")
    func keepsTopicToggleEnabledDuringModerationSave() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { _, _ in await gate.wait() }
        )
        let first = Task { await model.setModerationPushEnabled(false) }
        while !model.viewState.isSavingModeration { await Task.yield() }
        #expect(!model.viewState.isTopicDisabled)
        #expect(model.viewState.isModerationDisabled)
        await gate.open()
        await first.value
        #expect(!model.viewState.isSaving)
    }

    @Test("저장 실패 시 기존 토글로 복구하고 토스트용 오류를 전달한다")
    func keepsStateAfterSaveFailure() async {
        let viewModel = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { _, _ in throw SettingsAPIError.network }
        )
        await viewModel.setTopicPushEnabled(false)
        #expect(viewModel.viewState.settings == .init(topicPushEnabled: true, moderationPushEnabled: true))
        #expect(viewModel.viewState.errorMessage == nil)
        #expect(viewModel.saveErrorMessage != nil)
        viewModel.consumeSaveError()
        #expect(viewModel.saveErrorMessage == nil)
        #expect(!viewModel.viewState.isSaving)
    }

    @Test("저장 응답 전에도 주제 토글을 즉시 반영한다")
    func updatesTopicOptimistically() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: false, moderationPushEnabled: true)),
            saveSettings: { _, _ in await gate.wait() }
        )
        let task = Task { await model.setTopicPushEnabled(true) }
        while !model.viewState.isSavingTopic { await Task.yield() }
        #expect(model.viewState.settings?.topicPushEnabled == true)
        #expect(model.viewState.settings?.moderationPushEnabled == true)
        await gate.open()
        await task.value
    }

    @Test("검수 저장 실패는 다른 토글의 성공한 변경을 되돌리지 않는다")
    func rollsBackOnlyFailedModerationPreference() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: false, moderationPushEnabled: false)),
            saveSettings: { _, moderation in
                if moderation != nil { await gate.wait(); throw SettingsAPIError.network }
            }
        )
        let task = Task { await model.setModerationPushEnabled(true) }
        while !model.viewState.isSavingModeration { await Task.yield() }
        #expect(model.viewState.settings?.moderationPushEnabled == true)
        await model.setTopicPushEnabled(true)
        await gate.open()
        await task.value
        #expect(model.viewState.settings == .init(topicPushEnabled: true, moderationPushEnabled: false))
    }

    @Test("주제 저장 실패도 검수 토글의 성공한 변경을 유지한다")
    func rollsBackOnlyFailedTopicPreference() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: false, moderationPushEnabled: false)),
            saveSettings: { topic, _ in
                if topic != nil { await gate.wait(); throw SettingsAPIError.network }
            }
        )
        let task = Task { await model.setTopicPushEnabled(true) }
        while !model.viewState.isSavingTopic { await Task.yield() }
        await model.setModerationPushEnabled(true)
        await gate.open()
        await task.value
        #expect(model.viewState.settings == .init(topicPushEnabled: false, moderationPushEnabled: true))
    }

    @Test("저장 취소는 토글을 복구하되 오류 토스트를 표시하지 않는다")
    func restoresCancelledSaveWithoutToast() async {
        let gate = AsyncGate()
        let model = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: false, moderationPushEnabled: true)),
            saveSettings: { _, _ in await gate.wait() }
        )
        let task = Task { await model.setTopicPushEnabled(true) }
        while !model.viewState.isSavingTopic { await Task.yield() }
        task.cancel()
        await gate.open()
        await task.value
        #expect(model.viewState.settings == .init(topicPushEnabled: false, moderationPushEnabled: true))
        #expect(model.saveErrorMessage == nil)
        #expect(!model.viewState.isSaving)
    }

    @Test("reset 이후 늦게 끝난 조회 결과는 적용하지 않는다")
    func ignoresLateLoadAfterReset() async {
        let gate = AsyncGate()
        let viewModel = NotificationSettingsViewModel(loadSettings: {
            await gate.wait()
            return .init(topicPushEnabled: true, moderationPushEnabled: true)
        })
        let task = Task { await viewModel.load() }
        while !viewModel.viewState.isLoading { await Task.yield() }
        viewModel.reset()
        await gate.open()
        await task.value
        #expect(viewModel.viewState.settings == nil)
    }

    @Test("reset 이후 늦게 끝난 저장 결과는 이전 계정 상태를 복원하지 않는다")
    func ignoresLateSaveAfterReset() async {
        let gate = AsyncGate()
        let viewModel = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            saveSettings: { _, _ in await gate.wait() }
        )
        let task = Task { await viewModel.setTopicPushEnabled(false) }
        while !viewModel.viewState.isSaving { await Task.yield() }
        viewModel.reset()
        await gate.open()
        await task.value
        #expect(viewModel.viewState.settings == nil)
    }

    @Test("재조회 실패 시 오래된 서버 값으로 설정을 변경하지 않는다")
    func disablesStaleSettingsAfterReloadFailure() async {
        var saved = false
        let viewModel = NotificationSettingsViewModel(
            initialState: .init(settings: .init(topicPushEnabled: true, moderationPushEnabled: true)),
            loadSettings: { throw SettingsAPIError.network },
            saveSettings: { _, _ in saved = true }
        )
        await viewModel.load()
        await viewModel.setTopicPushEnabled(false)
        #expect(viewModel.viewState.settings == nil)
        #expect(!saved)
    }

    @Test("조회 실패 시 토글을 사용할 수 없고 재시도할 수 있다")
    func retriesLoadFailure() async throws {
        var attempts = 0
        let viewModel = NotificationSettingsViewModel(loadSettings: {
            attempts += 1
            if attempts == 1 { throw SettingsAPIError.network }
            return .init(topicPushEnabled: false, moderationPushEnabled: false)
        })
        await viewModel.load()
        #expect(viewModel.viewState.settings == nil)
        #expect(viewModel.viewState.errorMessage != nil)
        await viewModel.retry()
        #expect(viewModel.viewState.settings == .init(topicPushEnabled: false, moderationPushEnabled: false))
    }
}

@Suite(.serialized)
struct NotificationSettingsAPIClientTests {
    @Test("GET은 인증된 notification-settings 경로를 호출한다")
    func fetchesSettings() async throws {
        let recorder = NotificationSettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return response(request, body: #"{"topicPushEnabled":true,"moderationPushEnabled":false}"#)
        }
        let result = try await client.fetch()
        #expect(result.topicPushEnabled)
        #expect(!result.moderationPushEnabled)
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "GET")
        #expect(request.url?.path == "/api/v1/notification-settings")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    @Test("주제 알림 PATCH는 주제 설정만 보내고 204를 성공 처리한다")
    func updatesTopicFlagOnly() async throws {
        let recorder = NotificationSettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return response(request, statusCode: 204, body: "")
        }
        try await client.update(topicPushEnabled: false)
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "PATCH")
        let json = try #require(JSONSerialization.jsonObject(with: request.httpBody ?? Data()) as? [String: Bool])
        #expect(json == ["topicPushEnabled": false])
    }

    @Test("검수 알림 PATCH는 검수 설정만 보낸다")
    func updatesModerationFlagOnly() async throws {
        let recorder = NotificationSettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return response(request, statusCode: 204, body: "")
        }
        try await client.update(moderationPushEnabled: false)
        let request = try #require(await recorder.requests.first)
        let json = try #require(JSONSerialization.jsonObject(with: request.httpBody ?? Data()) as? [String: Bool])
        #expect(json == ["moderationPushEnabled": false])
    }

    @Test("PATCH는 204 본문 없는 응답을 반환한다")
    func handlesEmptyPatchResponse() async throws {
        let client = makeClient { request in
            response(request, statusCode: 204, body: "")
        }
        try await client.update(topicPushEnabled: true)
    }

    @Test("401은 인증 만료 오류로 변환한다")
    func mapsUnauthorized() async {
        let client = makeClient { request in response(request, statusCode: 401, body: "{}") }
        await #expect(throws: SettingsAPIError.unauthorized) { try await client.fetch() }
    }

    @Test("GET에서 필드가 없거나 null이면 invalidResponse다")
    func rejectsIncompleteGetResponse() async {
        for body in [#"{"topicPushEnabled":true}"#, #"{"topicPushEnabled":null,"moderationPushEnabled":true}"#] {
            let client = makeClient { request in response(request, body: body) }
            await #expect(throws: SettingsAPIError.invalidResponse) { try await client.fetch() }
        }
    }

    private func makeClient(
        handler: @escaping NotificationSettingsMockURLProtocol.Handler
    ) -> NotificationSettingsAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [NotificationSettingsMockURLProtocol.self]
        NotificationSettingsMockURLProtocol.handler = handler
        return NotificationSettingsAPIClient(
            baseURL: URL(string: "https://example.com/api/v1/"),
            session: URLSession(configuration: configuration),
            accessTokenProvider: { "access-token" }
        )
    }

    private func response(
        _ request: URLRequest,
        statusCode: Int = 200,
        body: String
    ) -> (HTTPURLResponse, Data) {
        (
            HTTPURLResponse(url: request.url!, statusCode: statusCode, httpVersion: nil, headerFields: nil)!,
            Data(body.utf8)
        )
    }
}

private actor AsyncGate {
    private var continuation: CheckedContinuation<Void, Never>?
    private var isOpen = false
    func wait() async {
        guard !isOpen else { return }
        await withCheckedContinuation { continuation = $0 }
    }
    func open() { isOpen = true; continuation?.resume(); continuation = nil }
}

private actor NotificationSettingsRequestRecorder {
    private(set) var requests: [URLRequest] = []
    func append(_ request: URLRequest) { requests.append(request) }
}

private final class NotificationSettingsMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)
    nonisolated(unsafe) static var handler: Handler?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let handler = Self.handler else { return }
        let readableRequest = Self.requestWithReadableBody(request)
        Task {
            do {
                let (response, data) = try await handler(readableRequest)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch { client?.urlProtocol(self, didFailWithError: error) }
        }
    }
    private static func requestWithReadableBody(_ request: URLRequest) -> URLRequest {
        guard request.httpBody == nil, let stream = request.httpBodyStream else { return request }
        var request = request
        var body = Data()
        stream.open()
        defer { stream.close() }
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            body.append(buffer, count: count)
        }
        request.httpBody = body
        return request
    }

    override func stopLoading() {}
}
