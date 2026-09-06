import SwiftUI

struct OnboardingTermsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var state = OnboardingTermsState()

    let onNext: () -> Void
    var onServiceTermsView: () -> Void = {}
    var onPrivacyPolicyView: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Spacer()
                    .frame(height: Metrics.titleTopPadding)

                Text("찰캌에\n오신 것을 환영합니다.")
                    .font(theme.typography.title1)
                    .foregroundStyle(theme.colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer()
                    .frame(height: Metrics.consentTopPadding)

                TermsAllConsentRow(
                    isChecked: state.isAllAgreed,
                    onToggle: { state.toggleAllConsent() }
                )

                Spacer()
                    .frame(height: Metrics.requiredTopPadding)

                TermsRequiredConsentRow(
                    title: "(필수) 서비스 이용약관",
                    isChecked: state.isServiceTermsAgreed,
                    onToggle: { state.toggleServiceTerms() },
                    onView: onServiceTermsView
                )

                TermsDivider()

                TermsRequiredConsentRow(
                    title: "(필수) 개인정보 처리방침",
                    isChecked: state.isPrivacyPolicyAgreed,
                    onToggle: { state.togglePrivacyPolicy() },
                    onView: onPrivacyPolicyView
                )

                TermsDivider()
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)

            Spacer(minLength: 0)

            ChalkakButton(
                title: "다음",
                action: {
                    guard state.isAllAgreed else { return }
                    onNext()
                },
                fillsWidth: true
            )
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.bottom, Metrics.bottomPadding)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.inputBackground)
        .accessibilityIdentifier("onboardingTermsScreen")
    }
}

private struct TermsAllConsentRow: View {
    @Environment(\.chalkakTheme) private var theme
    let isChecked: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: Metrics.allConsentItemSpacing) {
                ChalkakCheckbox(isChecked: isChecked)

                Text("전체 동의")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.textPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Metrics.allConsentHorizontalPadding)
            .padding(.vertical, Metrics.allConsentVerticalPadding)
            .background(theme.colors.inputBackground)
            .overlay {
                RoundedRectangle(cornerRadius: theme.shapes.large)
                    .stroke(Metrics.cardBorderColor, lineWidth: Metrics.borderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.large))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("전체 동의")
        .accessibilityValue(isChecked ? "선택됨" : "선택 안 됨")
    }
}

private struct TermsRequiredConsentRow: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    let isChecked: Bool
    let onToggle: () -> Void
    let onView: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: Metrics.requiredItemSpacing) {
                    ChalkakCheckbox(isChecked: isChecked)

                    Text(title)
                        .font(theme.typography.body)
                        .foregroundStyle(theme.colors.textPrimary.opacity(0.85))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.leading, Metrics.requiredLeadingPadding)
                .padding(.vertical, Metrics.requiredVerticalPadding)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
            .accessibilityValue(isChecked ? "선택됨" : "선택 안 됨")

            Button("보기", action: onView)
                .font(theme.typography.callout)
                .foregroundStyle(theme.colors.textMuted)
                .padding(.leading, Metrics.viewButtonLeadingPadding)
                .padding(.trailing, Metrics.viewButtonTrailingPadding)
                .padding(.vertical, Metrics.requiredVerticalPadding)
                .buttonStyle(.plain)
        }
    }
}

private struct TermsDivider: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        Rectangle()
            .fill(theme.colors.border)
            .frame(height: Metrics.borderWidth)
    }
}

private enum Metrics {
    static let titleTopPadding: CGFloat = 50
    static let consentTopPadding: CGFloat = 57
    static let requiredTopPadding: CGFloat = 3
    static let bottomPadding: CGFloat = 20
    static let allConsentHorizontalPadding: CGFloat = 16
    static let allConsentVerticalPadding: CGFloat = 19
    static let allConsentItemSpacing: CGFloat = 16
    static let requiredLeadingPadding: CGFloat = 4
    static let requiredVerticalPadding: CGFloat = 16
    static let requiredItemSpacing: CGFloat = 14
    static let viewButtonLeadingPadding: CGFloat = 12
    static let viewButtonTrailingPadding: CGFloat = 4
    static let borderWidth: CGFloat = 1
    static let cardBorderColor = Color(
        red: Double(0xE3) / 255,
        green: Double(0xE1) / 255,
        blue: Double(0xDD) / 255
    )
}

#Preview("Terms") {
    OnboardingTermsScreen(onNext: {})
        .chalkakTheme(.light)
}
