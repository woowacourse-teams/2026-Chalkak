import SwiftUI

struct LoginScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let onSocialLogin: (SocialLoginProvider) -> Void
    let onContinueAsGuest: () -> Void
    var isEnabled = true

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                header
                    .frame(height: proxy.size.height * Metrics.headerHeightRatio)

                actions
                    .frame(maxHeight: .infinity)
            }
        }
        .background(theme.colors.background)
        .ignoresSafeArea()
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            ChalkakImage(
                source: .asset("img_login_background"),
                contentDescription: nil,
                contentMode: .fill
            )
            .opacity(0.2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()

            Text("매일 하나의 주제,\n각자의 한 장")
                .font(theme.typography.display)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.horizontal, Metrics.titleHorizontalPadding)
                .padding(.top, Metrics.headerTopPadding)
        }
        .frame(maxWidth: .infinity)
        .clipped()
    }

    private var actions: some View {
        ViewThatFits(in: .vertical) {
            actionsContent

            ScrollView {
                actionsContent
            }
            .scrollIndicators(.hidden)
        }
    }

    private var actionsContent: some View {
        VStack(spacing: 0) {
            VStack(spacing: theme.spacing.md) {
                ForEach(SocialLoginProvider.allCases, id: \.self) { provider in
                    SocialLoginButton(
                        provider: provider,
                        action: { onSocialLogin(provider) },
                        isEnabled: isEnabled
                    )
                    .frame(maxWidth: .infinity)
                }
            }

            Button("로그인 없이 사진 둘러보기", action: onContinueAsGuest)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .underline()
                .frame(minHeight: Metrics.minimumTouchHeight)
                .buttonStyle(.plain)
                .disabled(!isEnabled)
                .padding(.top, Metrics.guestTopSpacing)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .padding(.top, Metrics.actionTopPadding)
        .padding(.bottom, Metrics.actionBottomPadding)
        .frame(maxWidth: .infinity)
        .fixedSize(horizontal: false, vertical: true)
    }
}

private enum Metrics {
    static let headerHeightRatio: CGFloat = 0.53
    static let headerTopPadding: CGFloat = 135
    static let titleHorizontalPadding: CGFloat = 20
    static let actionTopPadding: CGFloat = 60
    static let actionBottomPadding: CGFloat = 56
    static let guestTopSpacing: CGFloat = 24
    static let minimumTouchHeight: CGFloat = 44
}

#Preview("Login") {
    LoginScreen(onSocialLogin: { _ in }, onContinueAsGuest: {})
        .chalkakTheme(.light)
}
