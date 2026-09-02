import SwiftUI

struct SocialLoginButton: View {
    @Environment(\.chalkakTheme) private var theme

    let provider: SocialLoginProvider
    let action: () -> Void
    var isEnabled = true

    var body: some View {
        Button(action: action) {
            HStack(spacing: theme.spacing.md) {
                provider.icon

                Text(provider.buttonTitle)
                    .font(theme.typography.callout)
                    .foregroundStyle(isEnabled ? theme.colors.textPrimary : theme.colors.textMuted)
            }
            .frame(maxWidth: .infinity)
            .frame(height: Metrics.buttonHeight)
        }
        .background {
            RoundedRectangle(cornerRadius: theme.shapes.button)
                .fill(theme.colors.inputBackground)
        }
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.button)
                .stroke(theme.colors.border, lineWidth: Metrics.borderWidth)
        }
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.button))
        .contentShape(RoundedRectangle(cornerRadius: theme.shapes.button))
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(provider.buttonTitle)
    }
}

private extension SocialLoginProvider {
    @ViewBuilder
    var icon: some View {
        switch self {
        case .apple:
            Image("apple_logo")
                .resizable()
                .scaledToFit()
                .frame(width: Metrics.logoSize, height: Metrics.logoSize)
                .accessibilityHidden(true)
        case .google:
            Image("img_google_logo")
                .resizable()
                .scaledToFit()
                .frame(width: Metrics.logoSize, height: Metrics.logoSize)
                .accessibilityHidden(true)
        case .kakao:
            Image("img_kakao_logo")
                .resizable()
                .scaledToFit()
                .frame(width: Metrics.logoSize, height: Metrics.logoSize)
                .accessibilityHidden(true)
        }
    }
}

private enum Metrics {
    static let logoSize: CGFloat = 20
    static let buttonHeight: CGFloat = 56
    static let borderWidth: CGFloat = 1
}

#Preview("Social login buttons") {
    VStack(spacing: ChalkakSpacing.lg) {
        ForEach(SocialLoginProvider.allCases, id: \.self) { provider in
            SocialLoginButton(provider: provider, action: {})
        }
    }
    .padding(ChalkakSpacing.screenHorizontal)
    .chalkakTheme(.light)
}
