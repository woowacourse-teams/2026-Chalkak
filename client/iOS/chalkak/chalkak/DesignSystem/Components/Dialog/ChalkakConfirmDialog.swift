import SwiftUI

enum ChalkakConfirmDialogStyle {
    case primary
    case destructive
}

struct ChalkakConfirmDialog: View {
    @Environment(\.chalkakTheme) private var theme

    let title: String
    let message: String
    let confirmText: String
    let confirmStyle: ChalkakConfirmDialogStyle
    var isDismissible = true
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            theme.colors.scrim
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    guard isDismissible else { return }
                    onDismiss()
                }

            VStack(spacing: 0) {
                Text(title)
                    .font(theme.typography.title3)
                    .foregroundStyle(theme.colors.textPrimary)

                Text(message)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textMuted)
                    .padding(.top, Metrics.titleMessageSpacing)

                HStack(spacing: Metrics.buttonSpacing) {
                    if isDismissible {
                        actionButton(
                            title: "취소",
                            background: theme.colors.textInactive,
                            action: onDismiss
                        )
                    }

                    actionButton(
                        title: confirmText,
                        background: confirmColor,
                        action: onConfirm
                    )
                    .accessibilityIdentifier("confirmDialog.confirm")
                }
                .padding(.top, Metrics.messageButtonSpacing)
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
            .accessibilityIdentifier("confirmDialog")
        }
    }

    private var confirmColor: Color {
        switch confirmStyle {
        case .primary:
            theme.colors.actionPrimary
        case .destructive:
            theme.colors.error
        }
    }

    private func actionButton(
        title: String,
        background: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(theme.typography.callout)
                .foregroundStyle(theme.colors.onActionPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Metrics.buttonVerticalPadding)
                .background(background)
                .clipShape(RoundedRectangle(cornerRadius: theme.shapes.button))
        }
        .buttonStyle(.plain)
    }
}

private enum Metrics {
    static let width: CGFloat = 317
    static let horizontalPadding: CGFloat = 40
    static let topPadding: CGFloat = 24
    static let bottomPadding: CGFloat = 26
    static let titleMessageSpacing: CGFloat = 8
    static let messageButtonSpacing: CGFloat = 23
    static let buttonSpacing: CGFloat = 10
    static let buttonVerticalPadding: CGFloat = 9.5
}

#Preview("Logout dialog") {
    ChalkakConfirmDialog(
        title: "로그아웃",
        message: "정말 로그아웃 하시겠습니까?",
        confirmText: "로그아웃",
        confirmStyle: .primary,
        onConfirm: {},
        onDismiss: {}
    )
    .chalkakTheme(.light)
}
