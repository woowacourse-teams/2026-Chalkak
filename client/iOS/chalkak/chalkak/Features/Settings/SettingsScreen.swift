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
        case .logout: "로그아웃하면 다시 로그인해야 찰칵을 이용할 수 있어요."
        case .withdraw: "회원탈퇴하면 계정과 게시물이 삭제되며, 삭제한 내용은 복구할 수 없어요."
        }
    }
}

/// 설정 화면. 홈 상단 설정 버튼으로 열리는 push 화면이라 하단바 없이 뒤로가기 상단바("설정")를 둔다(#1021).
/// 알림 설정·고객센터는 이 화면 위에 쌓이고, 뒤로 가면 이 화면으로 돌아온다(쌓는 일은 `ContentView`가 한다).
struct SettingsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast

    @Bindable var viewModel: SettingsViewModel
    var onBackClick: () -> Void = {}
    var onLogin: () -> Void = {}
    var onPrivacyPolicy: () -> Void = {}
    var onTerms: () -> Void = {}
    var onOpenFeedback: () -> Void = {}
    var onOpenNotificationSettings: () -> Void = {}
    var onSignedOut: () -> Void = {}
    var onSignatureScreenChange: (AnalyticsScreen?) -> Void = { _ in }
    @State private var isSignatureChangePresented = false
    @State private var didUpdateSignature = false

    var body: some View {
        VStack(spacing: 0) {
            NotificationTopBar(title: "설정", onBackClick: onBackClick)
            content
        }
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

    private var content: some View {
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

                            SettingsNotificationEntryCard(onOpen: onOpenNotificationSettings)
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
            // 상단바가 생겨 하단 탭이던 때의 위 여백(48pt)은 두지 않는다.
            .padding(.top, theme.spacing.sm)
            .padding(.bottom, theme.spacing.xxl)
        }
        .scrollIndicators(.hidden)
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
        showToast(text)
    }

}

private enum Metrics {
    static let appToAccountSpacing: CGFloat = 36
    static let loadingHeight: CGFloat = 56
}

#Preview("회원 설정") {
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

#Preview("게스트 설정") {
    SettingsScreen(
        viewModel: SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, version: "1.0")
        )
    )
        .chalkakTheme(.light)
}
