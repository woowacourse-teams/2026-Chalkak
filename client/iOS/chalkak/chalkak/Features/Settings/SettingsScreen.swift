import Foundation
import SwiftUI
import UIKit
import UserNotifications

enum SettingsAccountDialog: Equatable {
    case logout
    case withdraw

    var title: String {
        switch self {
        case .logout: "로그아웃"
        case .withdraw: "회원탈퇴"
        }
    }

    var message: String {
        switch self {
        case .logout: "로그아웃하면 다시 로그인해야 찰칵을 이용할 수 있어요."
        case .withdraw: "회원탈퇴하면 계정과 게시물이 삭제되며, 삭제한 내용은 복구할 수 없어요."
        }
    }
}

struct SettingsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @Bindable var viewModel: SettingsViewModel
    @Bindable var notificationSettingsViewModel = NotificationSettingsViewModel()
    var onLogin: () -> Void = {}
    var onPrivacyPolicy: () -> Void = {}
    var onTerms: () -> Void = {}
    var onOpenFeedback: () -> Void = {}
    var onSignedOut: () -> Void = {}
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }
    var onOpenPhotoUpload: () -> Void = {}
    var onSignatureScreenChange: (AnalyticsScreen?) -> Void = { _ in }

    var bottomBarCompact: Binding<Bool> = .constant(false)
    @State private var isSignatureChangePresented = false
    @State private var didUpdateSignature = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if viewModel.viewState.isLoading || viewModel.viewState.isLoggedIn {
                    SettingsSectionLabel(text: "앱 설정")

                    Group {
                        if viewModel.viewState.isLoading {
                            Color.clear.frame(height: Metrics.loadingHeight)
                        } else {
                            SettingsSignatureCard(
                                signatureSource: viewModel.viewState.signatureURL.map(ChalkakImageSource.remote),
                                onChange: { isSignatureChangePresented = true }
                            )

                            SettingsNotificationCard(
                                topicPushEnabled: notificationSettingsViewModel.viewState.settings?.topicPushEnabled ?? false,
                                moderationPushEnabled: notificationSettingsViewModel.viewState.settings?.moderationPushEnabled ?? false,
                                aiReviewPushEnabled: notificationSettingsViewModel.viewState.settings?.aiReviewPushEnabled ?? false,
                                isTopicDisabled: notificationSettingsViewModel.viewState.isTopicDisabled,
                                isModerationDisabled: notificationSettingsViewModel.viewState.isModerationDisabled,
                                isAIReviewDisabled: notificationSettingsViewModel.viewState.isAIReviewDisabled,
                                errorMessage: notificationSettingsViewModel.viewState.errorMessage,
                                onTopicToggle: { enabled in
                                    Task {
                                        async let save: Void = notificationSettingsViewModel.setTopicPushEnabled(enabled)
                                        await openNotificationSettingsIfNeeded(enabled: enabled)
                                        await save
                                    }
                                },
                                onModerationToggle: { enabled in
                                    Task {
                                        async let save: Void = notificationSettingsViewModel.setModerationPushEnabled(enabled)
                                        await openNotificationSettingsIfNeeded(enabled: enabled)
                                        await save
                                    }
                                },
                                onAIReviewToggle: { enabled in
                                    Task {
                                        async let save: Void = notificationSettingsViewModel.setAIReviewPushEnabled(enabled)
                                        await openNotificationSettingsIfNeeded(enabled: enabled)
                                        await save
                                    }
                                },
                                onRetry: {
                                    Task { await notificationSettingsViewModel.retry() }
                                }
                            )
                            .padding(.top, theme.spacing.lg)
                        }
                    }
                    .padding(.top, theme.spacing.lg)
                }

                SettingsSectionLabel(text: "계정")
                    .padding(.top, viewModel.viewState.isLoading || viewModel.viewState.isLoggedIn
                        ? Metrics.appToAccountSpacing : 0)

                Group {
                    if viewModel.viewState.isLoggedIn {
                        SettingsAccountCard(
                            onLogout: viewModel.showLogoutDialog,
                            onWithdraw: viewModel.showWithdrawDialog,
                            isEnabled: !viewModel.viewState.isAccountActionInProgress
                        )
                    } else if !viewModel.viewState.isLoading {
                        SettingsLoginButton(onClick: onLogin)
                    } else {
                        Color.clear.frame(height: Metrics.loadingHeight)
                    }
                }
                .padding(.top, theme.spacing.lg)

                SettingsSectionLabel(text: "피드백")
                    .padding(.top, theme.spacing.xxl)

                SettingsFeedbackCard(onFeedback: onOpenFeedback)
                    .padding(.top, theme.spacing.lg)

                SettingsSectionLabel(text: "정보 및 약관")
                    .padding(.top, theme.spacing.xxl)

                SettingsInformationCard(
                    version: viewModel.viewState.version,
                    onPrivacyPolicy: onPrivacyPolicy,
                    onTerms: onTerms
                )
                .padding(.top, theme.spacing.lg)
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, Metrics.topPadding)
            .padding(.bottom, theme.spacing.xxl)
        }
        .scrollIndicators(.hidden)
        .chalkakBottomBarScrollBehavior(isCompact: bottomBarCompact)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .alert(
            viewModel.viewState.accountDialog?.title ?? "",
            isPresented: Binding(
                get: { viewModel.viewState.accountDialog != nil },
                set: { if !$0 { viewModel.dismissAccountDialog() } }
            ),
            presenting: viewModel.viewState.accountDialog
        ) { accountDialog in
            Button("취소", role: .cancel, action: viewModel.dismissAccountDialog)
            Button(accountDialog.title, role: accountDialog == .withdraw ? .destructive : nil) {
                Task { await viewModel.confirmAccountAction(for: accountDialog) }
            }
        } message: { accountDialog in
            Text(accountDialog.message)
        }
        .task {
            await viewModel.load()
            if viewModel.viewState.isLoggedIn {
                await notificationSettingsViewModel.load()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task {
                    if viewModel.viewState.isLoggedIn {
                        await notificationSettingsViewModel.load()
                    }
                }
            }
        }
        .onChange(of: notificationSettingsViewModel.saveErrorMessage) { _, error in
            guard let error else { return }
            showMessage(error)
            notificationSettingsViewModel.consumeSaveError()
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .fullScreenCover(isPresented: $isSignatureChangePresented, onDismiss: {
            onSignatureScreenChange(nil)
            if didUpdateSignature {
                didUpdateSignature = false
                showMessage("사인 반영까지 시간이 좀 걸릴 수 있어요!")
            }
        }) {
            SignatureChangeFlow(
                onUpdate: { data in
                    try await viewModel.updateSignature(data)
                    didUpdateSignature = true
                },
                onScreenChange: { onSignatureScreenChange($0) }
            )
        }
    }

    @MainActor
    private func openNotificationSettingsIfNeeded(enabled: Bool) async {
        guard enabled else { return }
        let model = notificationSettingsViewModel
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        guard model === notificationSettingsViewModel, viewModel.viewState.isLoggedIn,
              NotificationTogglePermissionAction.shouldOpenSettings(enabled: enabled, status: status),
              let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        openURL(url)
    }

    private func handle(_ event: SettingsEvent?) {
        guard let event else { return }
        switch event {
        case let .showMessage(text):
            showMessage(text)
        case .signedOut:
            notificationSettingsViewModel.reset()
            onSignedOut()
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        showToast(text)
    }

}

private enum Metrics {
    static let topPadding: CGFloat = 48
    static let appToAccountSpacing: CGFloat = 36
    static let loadingHeight: CGFloat = 56
}

#Preview("Member settings") {
    SettingsScreen(
        viewModel: SettingsViewModel(
            initialState: SettingsViewState(
                isLoading: false,
                isLoggedIn: true,
                signatureURL: nil,
                version: "1.0"
            ),
            isAuthenticated: { true }
        ),
        notificationSettingsViewModel: NotificationSettingsViewModel()
    )
        .chalkakTheme(.light)
}

#Preview("Guest settings") {
    SettingsScreen(
        viewModel: SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, version: "1.0")
        ),
        notificationSettingsViewModel: NotificationSettingsViewModel()
    )
        .chalkakTheme(.light)
}
