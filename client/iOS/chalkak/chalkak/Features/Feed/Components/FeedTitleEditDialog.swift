import SwiftUI

struct FeedTitleEditDialog: View {
    @Environment(\.chalkakTheme) private var theme

    @Binding var title: String
    let isSubmitting: Bool
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            theme.colors.scrim
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    guard !isSubmitting else { return }
                    onDismiss()
                }

            VStack(spacing: 0) {
                Text("게시물 제목 수정")
                    .font(theme.typography.title3)
                    .foregroundStyle(theme.colors.textPrimary)

                ChalkakTextField(
                    text: $title,
                    label: "게시물 제목",
                    placeholder: "제목을 입력해 주세요.",
                    isEnabled: !isSubmitting,
                    lineLimit: 1...1,
                    textFont: theme.typography.subheadline,
                    maximumCharacterCount: Constants.titleMaxLength
                )
                .padding(.top, theme.spacing.lg)

                HStack(spacing: Metrics.buttonSpacing) {
                    ChalkakOutlinedButton(
                        title: "취소",
                        action: onDismiss,
                        isEnabled: !isSubmitting,
                        fillsWidth: true,
                        verticalPadding: Metrics.buttonVerticalPadding
                    )
                    ChalkakButton(
                        title: isSubmitting ? "저장 중..." : "저장",
                        action: onConfirm,
                        isEnabled: !isSubmitting,
                        fillsWidth: true,
                        verticalPadding: Metrics.buttonVerticalPadding
                    )
                }
                .padding(.top, theme.spacing.lg)
            }
            .padding(.horizontal, Metrics.horizontalPadding)
            .padding(.top, Metrics.topPadding)
            .padding(.bottom, Metrics.bottomPadding)
            .frame(maxWidth: Metrics.width)
            .background(theme.colors.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
            .shadow(color: .black.opacity(0.12), radius: 18, y: 8)
            .padding(.horizontal, theme.spacing.xl)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityIdentifier("titleEditDialog")
        }
    }
}

private enum Constants {
    static let titleMaxLength = 10
}

private enum Metrics {
    static let width: CGFloat = 340
    static let horizontalPadding: CGFloat = 24
    static let topPadding: CGFloat = 24
    static let bottomPadding: CGFloat = 26
    static let buttonSpacing: CGFloat = 10
    static let buttonVerticalPadding: CGFloat = 14
}
