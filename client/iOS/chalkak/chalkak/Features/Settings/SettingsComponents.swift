import SwiftUI

struct SettingsSectionLabel: View {
    @Environment(\.chalkakTheme) private var theme
    let text: String

    var body: some View {
        Text(text)
            .font(theme.typography.callout)
            .foregroundStyle(theme.colors.textMuted)
    }
}

struct SettingsSignatureCard: View {
    @Environment(\.chalkakTheme) private var theme
    let signatureSource: ChalkakImageSource?
    let onChange: () -> Void

    var body: some View {
        SettingsCard {
            HStack(spacing: theme.spacing.md) {
                Image(systemName: "pencil.tip")
                    .font(.system(size: Metrics.signatureIconSize, weight: .medium))
                    .foregroundStyle(theme.colors.iconSecondary)
                    .frame(
                        width: Metrics.signatureIconBackgroundSize,
                        height: Metrics.signatureIconBackgroundSize
                    )
                    .background(theme.colors.inputBackground, in: Circle())
                    .accessibilityHidden(true)

                Text("사인 재설정")
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textPrimary)

                Spacer(minLength: 0)

                Button("변경하기", action: onChange)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textSecondary)
                    .underline()
                    .buttonStyle(.plain)
                    .padding(.vertical, theme.spacing.xs)
            }
            .padding(.horizontal, Metrics.cardHorizontalPadding)
            .padding(.top, Metrics.signatureHeaderTopPadding)

            ZStack {
                RoundedRectangle(cornerRadius: theme.shapes.button)
                    .fill(theme.colors.actionPrimary)

                RoundedRectangle(cornerRadius: theme.shapes.button)
                    .stroke(
                        theme.colors.textOnImage.opacity(0.28),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 3])
                    )

                if let signatureSource {
                    ChalkakImage(
                        source: signatureSource,
                        contentDescription: "현재 사인",
                        contentMode: .fit
                    )
                    .frame(
                        maxWidth: Metrics.signatureImageWidth,
                        maxHeight: Metrics.signatureImageHeight
                    )
                }
            }
            .frame(height: Metrics.signatureCanvasHeight)
            .padding(.horizontal, Metrics.cardHorizontalPadding)
            .padding(.top, theme.spacing.lg)
            .padding(.bottom, Metrics.cardBottomPadding)
        }
    }
}

struct SettingsLoginButton: View {
    @Environment(\.chalkakTheme) private var theme
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack {
                Text("로그인")
                    .font(theme.typography.callout)

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.system(size: Metrics.chevronSize, weight: .semibold))
                    .accessibilityHidden(true)
            }
            .foregroundStyle(theme.colors.onActionPrimary)
            .padding(.horizontal, Metrics.cardHorizontalPadding)
            .padding(.vertical, Metrics.rowVerticalPadding)
            .frame(maxWidth: .infinity)
            .background(theme.colors.actionPrimary)
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
            .settingsCardShadow()
        }
        .buttonStyle(.plain)
    }
}

struct SettingsInformationCard: View {
    @Environment(\.chalkakTheme) private var theme
    let version: String
    let onPrivacyPolicy: () -> Void
    let onTerms: () -> Void

    var body: some View {
        SettingsCard {
            SettingsRow(title: "개인정보처리방침", action: onPrivacyPolicy) {
                SettingsChevron()
            }
            SettingsDivider()
            SettingsRow(title: "이용약관", action: onTerms) {
                SettingsChevron()
            }
            SettingsDivider()
            SettingsRow(title: "버전정보") {
                Text(version)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textMuted)
            }
        }
    }
}

struct SettingsAccountCard: View {
    @Environment(\.chalkakTheme) private var theme
    let onLogout: () -> Void
    let onWithdraw: () -> Void
    var isEnabled = true

    var body: some View {
        SettingsCard {
            SettingsRow(title: "로그아웃", action: onLogout) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: Metrics.accountIconSize, weight: .regular))
                    .foregroundStyle(theme.colors.textMuted)
                    .accessibilityHidden(true)
            }
            SettingsDivider()
            SettingsRow(
                title: "회원탈퇴",
                textColor: theme.colors.error,
                action: onWithdraw
            )
        }
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.55)
    }
}

private struct SettingsCard<Content: View>: View {
    @Environment(\.chalkakTheme) private var theme
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity)
        .background(theme.colors.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
        .settingsCardShadow()
    }
}

private struct SettingsRow<Trailing: View>: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var textColor: Color?
    var action: (() -> Void)?
    @ViewBuilder let trailing: Trailing

    init(
        title: String,
        textColor: Color? = nil,
        action: (() -> Void)? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.textColor = textColor
        self.action = action
        self.trailing = trailing()
    }

    var body: some View {
        Group {
            if let action {
                Button(action: action) { rowContent }
                    .buttonStyle(.plain)
            } else {
                rowContent
            }
        }
    }

    private var rowContent: some View {
        HStack {
            Text(title)
                .font(theme.typography.callout)
                .foregroundStyle(textColor ?? theme.colors.textPrimary)

            Spacer(minLength: 0)
            trailing
        }
        .padding(.horizontal, Metrics.cardHorizontalPadding)
        .padding(.vertical, Metrics.rowVerticalPadding)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
    }
}

private extension SettingsRow where Trailing == EmptyView {
    init(
        title: String,
        textColor: Color? = nil,
        action: (() -> Void)? = nil
    ) {
        self.init(title: title, textColor: textColor, action: action) {
            EmptyView()
        }
    }
}

private struct SettingsDivider: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        Rectangle()
            .fill(theme.colors.border)
            .frame(height: 1)
            .padding(.horizontal, Metrics.cardHorizontalPadding)
            .accessibilityHidden(true)
    }
}

private struct SettingsChevron: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: Metrics.chevronSize, weight: .semibold))
            .foregroundStyle(theme.colors.textMuted)
            .accessibilityHidden(true)
    }
}

private extension View {
    func settingsCardShadow() -> some View {
        shadow(color: .black.opacity(0.08), radius: 2, y: 1)
    }
}

private enum Metrics {
    static let cardHorizontalPadding: CGFloat = 20
    static let cardBottomPadding: CGFloat = 20
    static let rowVerticalPadding: CGFloat = 16
    static let signatureHeaderTopPadding: CGFloat = 18
    static let signatureCanvasHeight: CGFloat = 112
    static let signatureImageWidth: CGFloat = 120
    static let signatureImageHeight: CGFloat = 48
    static let signatureIconSize: CGFloat = 15
    static let signatureIconBackgroundSize: CGFloat = 30
    static let chevronSize: CGFloat = 13
    static let accountIconSize: CGFloat = 13
}
