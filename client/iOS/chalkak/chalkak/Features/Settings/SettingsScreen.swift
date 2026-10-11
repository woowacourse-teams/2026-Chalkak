import Foundation
import SwiftUI
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
        case .logout: "정말 로그아웃 하시겠습니까?"
        case .withdraw: "정말 회원탈퇴 하시겠습니까?"
        }
    }

    var style: ChalkakConfirmDialogStyle {
        switch self {
        case .logout: .primary
        case .withdraw: .destructive
        }
    }
}

struct SettingsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @Bindable var viewModel: SettingsViewModel
    var onLogin: () -> Void = {}
    var onPrivacyPolicy: () -> Void = {}
    var onTerms: () -> Void = {}
    var onOpenFeedback: () -> Void = {}
    var onSignedOut: () -> Void = {}
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }
    var onOpenPhotoUpload: () -> Void = {}
    var onOpenNotificationSetup: () -> Void = {}

    @State private var message: String?
    var bottomBarCompact: Binding<Bool> = .constant(false)
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var isSignatureChangePresented = false
    @State private var notificationAuthorizationStatus: UNAuthorizationStatus?
    @State private var isRequestingNotificationPermission = false

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
                                permissionStatus: notificationPermissionLabel,
                                onOpenNotificationPermission: {
                                    Task { await openNotificationPermission() }
                                },
                                onOpenNotificationSetup: onOpenNotificationSetup
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
        .overlay {
            if let accountDialog = viewModel.viewState.accountDialog {
                ChalkakConfirmDialog(
                    title: accountDialog.title,
                    message: accountDialog.message,
                    confirmText: accountDialog.title,
                    confirmStyle: accountDialog.style,
                    onConfirm: { Task { await viewModel.confirmAccountAction() } },
                    onDismiss: viewModel.dismissAccountDialog
                )
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .overlay(alignment: .bottom) { toast }
        .animation(.easeOut(duration: 0.16), value: viewModel.viewState.accountDialog)
        .task {
            await refreshNotificationPermission()
            await viewModel.load()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await refreshNotificationPermission() }
            }
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
        .fullScreenCover(isPresented: $isSignatureChangePresented) {
            SignatureChangeFlow(onUpdate: viewModel.updateSignature)
        }
    }

    private var notificationPermissionLabel: String {
        switch notificationAuthorizationStatus {
        case .authorized, .provisional, .ephemeral: "허용됨"
        case .denied: "꺼짐"
        case .notDetermined: "미설정"
        default: "확인 중"
        }
    }

    private func refreshNotificationPermission() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationAuthorizationStatus = settings.authorizationStatus
    }

    private func openNotificationPermission() async {
        guard !isRequestingNotificationPermission else { return }
        isRequestingNotificationPermission = true
        defer { isRequestingNotificationPermission = false }
        await refreshNotificationPermission()
        if notificationAuthorizationStatus == .notDetermined {
            do {
                let granted = try await UNUserNotificationCenter.current()
                    .requestAuthorization(options: [.alert, .sound, .badge])
                await refreshNotificationPermission()
                if granted { PushDeviceRegistrar.shared.registerCurrentDevice() }
            } catch {
                showMessage("알림 권한을 요청하지 못했어요. 다시 시도해 주세요.")
            }
        } else if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
            openURL(url)
        }
    }

    @ViewBuilder
    private var toast: some View {
        if let message {
            Text(message)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.onActionPrimary)
                .padding(.horizontal, theme.spacing.lg)
                .padding(.vertical, theme.spacing.md)
                .background(theme.colors.actionPrimary, in: Capsule())
                .padding(.bottom, Metrics.toastBottomPadding)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityLabel(message)
        }
    }

    private func handle(_ event: SettingsEvent?) {
        guard let event else { return }
        switch event {
        case let .showMessage(text):
            showMessage(text)
        case .signedOut:
            onSignedOut()
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task {
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }

}

private enum Metrics {
    static let topPadding: CGFloat = 48
    static let appToAccountSpacing: CGFloat = 36
    static let loadingHeight: CGFloat = 56
    static let toastBottomPadding: CGFloat = 88
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
        )
    )
        .chalkakTheme(.light)
}

#Preview("Guest settings") {
    SettingsScreen(
        viewModel: SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, version: "1.0")
        )
    )
        .chalkakTheme(.light)
}
