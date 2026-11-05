import SwiftUI
import UIKit
import UserNotifications

struct NotificationSettingsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @Bindable var viewModel: NotificationSettingsViewModel
    let onBackClick: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            NotificationTopBar(title: "알림 설정", onBackClick: onBackClick)

            if viewModel.viewState.isAwaitingFirstLoad {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    notificationCard
                        .padding(.horizontal, theme.spacing.screenHorizontal)
                        .padding(.vertical, theme.spacing.lg)
                }
                .scrollIndicators(.hidden)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .task {
            await viewModel.load()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await viewModel.load() }
            }
        }
        .onChange(of: viewModel.saveErrorMessage) { _, error in
            guard let error else { return }
            showToast(error)
            viewModel.consumeSaveError()
        }
    }

    private var notificationCard: some View {
        SettingsNotificationCard(
            topicPushEnabled: viewModel.viewState.settings?.topicPushEnabled ?? false,
            moderationPushEnabled: viewModel.viewState.settings?.moderationPushEnabled ?? false,
            aiReviewPushEnabled: viewModel.viewState.settings?.aiReviewPushEnabled ?? false,
            likePushEnabled: viewModel.viewState.settings?.likePushEnabled ?? false,
            isTopicDisabled: viewModel.viewState.isTopicDisabled,
            isModerationDisabled: viewModel.viewState.isModerationDisabled,
            isAIReviewDisabled: viewModel.viewState.isAIReviewDisabled,
            isLikeDisabled: viewModel.viewState.isLikeDisabled,
            errorMessage: viewModel.viewState.errorMessage,
            onTopicToggle: { enabled in
                Task {
                    async let save: Void = viewModel.setTopicPushEnabled(enabled)
                    await openNotificationSettingsIfNeeded(enabled: enabled)
                    await save
                }
            },
            onModerationToggle: { enabled in
                Task {
                    async let save: Void = viewModel.setModerationPushEnabled(enabled)
                    await openNotificationSettingsIfNeeded(enabled: enabled)
                    await save
                }
            },
            onAIReviewToggle: { enabled in
                Task {
                    async let save: Void = viewModel.setAIReviewPushEnabled(enabled)
                    await openNotificationSettingsIfNeeded(enabled: enabled)
                    await save
                }
            },
            onLikeToggle: { enabled in
                Task {
                    async let save: Void = viewModel.setLikePushEnabled(enabled)
                    await openNotificationSettingsIfNeeded(enabled: enabled)
                    await save
                }
            },
            onRetry: {
                Task { await viewModel.retry() }
            }
        )
    }

    @MainActor
    private func openNotificationSettingsIfNeeded(enabled: Bool) async {
        guard enabled else { return }
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        guard NotificationTogglePermissionAction.shouldOpenSettings(enabled: enabled, status: status),
              let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        openURL(url)
    }
}

#Preview("Notification settings") {
    NotificationSettingsScreen(
        viewModel: NotificationSettingsViewModel(
            initialState: NotificationSettingsViewState(
                settings: NotificationSettings(
                    topicPushEnabled: true,
                    moderationPushEnabled: true,
                    aiReviewPushEnabled: false,
                    likePushEnabled: true
                )
            )
        ),
        onBackClick: {}
    )
    .chalkakTheme(.light)
}
