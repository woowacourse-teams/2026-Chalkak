import Foundation
import SwiftUI

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

    @Bindable var viewModel: SettingsViewModel
    var onLogin: () -> Void = {}
    var onPrivacyPolicy: () -> Void = {}
    var onTerms: () -> Void = {}
    var onSignedOut: () -> Void = {}
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }
    var onOpenPhotoUpload: () -> Void = {}

    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var isSignatureChangePresented = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                SettingsSectionLabel(text: "앱 설정")

                Group {
                    if viewModel.viewState.isLoading {
                        Color.clear.frame(height: Metrics.loadingHeight)
                    } else if viewModel.viewState.isLoggedIn {
                        SettingsSignatureCard(
                            signatureSource: viewModel.viewState.signatureURL.map(ChalkakImageSource.remote),
                            onChange: { isSignatureChangePresented = true }
                        )
                    } else {
                        SettingsLoginButton(onClick: onLogin)
                    }
                }
                .padding(.top, theme.spacing.lg)

                SettingsSectionLabel(text: "정보 및 약관")
                    .padding(.top, Metrics.appToInformationSpacing)

                SettingsInformationCard(
                    version: viewModel.viewState.version,
                    onPrivacyPolicy: onPrivacyPolicy,
                    onTerms: onTerms
                )
                .padding(.top, theme.spacing.lg)

                if viewModel.viewState.isLoggedIn {
                    SettingsAccountCard(
                        onLogout: viewModel.showLogoutDialog,
                        onWithdraw: viewModel.showWithdrawDialog,
                        isEnabled: !viewModel.viewState.isAccountActionInProgress
                    )
                    .padding(.top, theme.spacing.xxl)
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, Metrics.topPadding)
            .padding(.bottom, theme.spacing.xxl)
        }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakBottomBar(
                selectedItem: .settings,
                onSelect: onNavigateToBottomBar,
                onAdd: onOpenPhotoUpload
            )
        }
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
            await viewModel.load()
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

    static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
            ?? "-"
    }
}

private enum Metrics {
    static let topPadding: CGFloat = 48
    static let appToInformationSpacing: CGFloat = 36
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
