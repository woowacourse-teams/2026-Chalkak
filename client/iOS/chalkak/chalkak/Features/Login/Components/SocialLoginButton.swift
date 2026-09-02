import AuthenticationServices
import SwiftUI

struct SocialLoginButton: View {
    @Environment(\.chalkakTheme) private var theme

    let provider: SocialLoginProvider
    let action: () -> Void
    var isEnabled = true

    @ViewBuilder
    var body: some View {
        switch provider {
        case .apple:
            AppleAuthorizationButton(
                action: action,
                isEnabled: isEnabled,
                cornerRadius: theme.shapes.button
            )
            .frame(height: Metrics.buttonHeight)
        case .google, .kakao:
            brandedButton
        }
    }

    private var brandedButton: some View {
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

private struct AppleAuthorizationButton: UIViewRepresentable {
    let action: () -> Void
    let isEnabled: Bool
    let cornerRadius: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(action: action)
    }

    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .continue, style: .whiteOutline)
        button.addTarget(
            context.coordinator,
            action: #selector(Coordinator.performAction),
            for: .touchUpInside
        )
        return button
    }

    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {
        context.coordinator.action = action
        button.isEnabled = isEnabled
        button.cornerRadius = cornerRadius
    }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc func performAction() {
            action()
        }
    }
}

private extension SocialLoginProvider {
    @ViewBuilder
    var icon: some View {
        switch self {
        case .apple:
            EmptyView()
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
