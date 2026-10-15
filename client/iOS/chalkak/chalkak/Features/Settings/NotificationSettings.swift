import Foundation
import Observation
import UserNotifications

struct NotificationSettings: Codable, Equatable, Sendable {
    let topicPushEnabled: Bool
    let moderationPushEnabled: Bool
}

struct NotificationSettingsViewState: Equatable, Sendable {
    var settings: NotificationSettings?
    var isLoading = false
    var isSavingTopic = false
    var isSavingModeration = false
    var isSaving: Bool { isSavingTopic || isSavingModeration }
    var isTopicDisabled: Bool { isLoading || isSavingTopic || settings == nil }
    var isModerationDisabled: Bool { isLoading || isSavingModeration || settings == nil }
    var errorMessage: String?
}

@MainActor
@Observable
final class NotificationSettingsViewModel {
    typealias Loader = @MainActor @Sendable () async throws -> NotificationSettings
    typealias Saver = @MainActor @Sendable (Bool?, Bool?) async throws -> Void

    private(set) var saveErrorMessage: String?
    private(set) var viewState: NotificationSettingsViewState
    private let loadSettings: Loader
    private let saveSettings: Saver
    private var generation = 0

    init(
        initialState: NotificationSettingsViewState = NotificationSettingsViewState(),
        loadSettings: @escaping Loader = { throw SettingsAPIError.configuration },
        saveSettings: @escaping Saver = { _, _ in throw SettingsAPIError.configuration }
    ) {
        viewState = initialState
        self.loadSettings = loadSettings
        self.saveSettings = saveSettings
    }

    func load() async {
        guard !viewState.isLoading, !viewState.isSaving else { return }
        let requestGeneration = generation
        viewState.isLoading = true
        viewState.errorMessage = nil
        defer { if requestGeneration == generation { viewState.isLoading = false } }
        do {
            let settings = try await loadSettings()
            guard requestGeneration == generation else { return }
            viewState.settings = settings
        } catch is CancellationError {
            return
        } catch {
            guard requestGeneration == generation else { return }
            viewState.settings = nil
            viewState.errorMessage = "알림 설정을 불러오지 못했어요. 다시 시도해 주세요."
        }
    }

    func retry() async { await load() }

    func reset() {
        generation += 1
        saveErrorMessage = nil
        viewState = NotificationSettingsViewState()
    }

    func consumeSaveError() { saveErrorMessage = nil }

    func setTopicPushEnabled(_ enabled: Bool) async {
        _ = await update(topicPushEnabled: enabled, moderationPushEnabled: nil)
    }

    func setModerationPushEnabled(_ enabled: Bool) async {
        _ = await update(topicPushEnabled: nil, moderationPushEnabled: enabled)
    }

    func setAllPushEnabled(_ enabled: Bool) async -> Bool {
        await update(topicPushEnabled: enabled, moderationPushEnabled: enabled)
    }

    private func update(topicPushEnabled: Bool?, moderationPushEnabled: Bool?) async -> Bool {
        guard !viewState.isLoading,
              topicPushEnabled == nil || !viewState.isSavingTopic,
              moderationPushEnabled == nil || !viewState.isSavingModeration,
              topicPushEnabled != nil || moderationPushEnabled != nil,
              let topic = topicPushEnabled ?? viewState.settings?.topicPushEnabled,
              let moderation = moderationPushEnabled ?? viewState.settings?.moderationPushEnabled
        else { return false }

        let requestGeneration = generation
        let previousSettings = viewState.settings
        viewState.settings = NotificationSettings(topicPushEnabled: topic, moderationPushEnabled: moderation)
        saveErrorMessage = nil
        if topicPushEnabled != nil { viewState.isSavingTopic = true }
        if moderationPushEnabled != nil { viewState.isSavingModeration = true }
        viewState.errorMessage = nil
        defer {
            if requestGeneration == generation {
                if topicPushEnabled != nil { viewState.isSavingTopic = false }
                if moderationPushEnabled != nil { viewState.isSavingModeration = false }
            }
        }
        do {
            try await saveSettings(topicPushEnabled, moderationPushEnabled)
            try Task.checkCancellation()
            guard requestGeneration == generation else { return false }
            return true
        } catch {
            guard requestGeneration == generation else { return false }
            // 각 요청이 바꾼 항목만 복구해 다른 토글의 동시 변경을 보존한다.
            if let previousSettings {
                viewState.settings = NotificationSettings(
                    topicPushEnabled: topicPushEnabled != nil
                        ? previousSettings.topicPushEnabled
                        : viewState.settings?.topicPushEnabled ?? previousSettings.topicPushEnabled,
                    moderationPushEnabled: moderationPushEnabled != nil
                        ? previousSettings.moderationPushEnabled
                        : viewState.settings?.moderationPushEnabled ?? previousSettings.moderationPushEnabled
                )
            } else {
                viewState.settings = nil
            }
            if !(error is CancellationError), !Task.isCancelled {
                saveErrorMessage = "알림 설정을 저장하지 못했어요. 다시 시도해 주세요."
            }
            return false
        }
    }
}


// 서버 설정과 OS 권한은 별개다. 켜기 동작에서만 차단 해제를 안내한다.
enum NotificationTogglePermissionAction {
    static func shouldOpenSettings(enabled: Bool, status: UNAuthorizationStatus) -> Bool {
        enabled && status == .denied
    }
}
