import SwiftUI

struct FeedTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    let onBack: () -> Void
    var onEdit: () -> Void = {}
    var onDelete: () -> Void = {}
    var isEditVisible = false
    var isDeleteVisible = false
    var isEditEnabled = true
    var isDeleteEnabled = true

    var body: some View {
        HStack(spacing: 0) {
            ChalkakNavigationButton(kind: .back, action: onBack)

            Spacer(minLength: 0)

            if isEditVisible || isDeleteVisible {
                HStack(spacing: theme.spacing.md) {
                    if isEditVisible {
                        actionButton(
                            title: "수정",
                            imageName: "ic_edit",
                            color: theme.colors.actionPrimary,
                            isEnabled: isEditEnabled,
                            action: onEdit
                        )
                    }
                    if isDeleteVisible {
                        actionButton(
                            title: "삭제",
                            imageName: "ic_delete",
                            color: theme.colors.error,
                            isEnabled: isDeleteEnabled,
                            action: onDelete
                        )
                    }
                }
            }
        }
    }

    private func actionButton(
        title: String,
        imageName: String,
        color: Color,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(imageName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: Metrics.actionIconSize, height: Metrics.actionIconSize)
        }
            .foregroundStyle(color)
            .tint(color)
            .controlSize(.large)
            .modifier(ChalkakNavigationButtonStyle())
            .buttonBorderShape(.circle)
            .frame(
                width: ChalkakNavigationButton.diameter,
                height: ChalkakNavigationButton.diameter
            )
            .disabled(!isEnabled)
            .accessibilityLabel(title)
    }
}

private enum Metrics {
    static let actionIconSize: CGFloat = 24
}

#Preview("Feed Top Bar", traits: .sizeThatFitsLayout) {
    FeedTopBar(onBack: {}, isEditVisible: true, isDeleteVisible: true)
        .padding(.horizontal, ChalkakSpacing.lg)
        .padding(.vertical, ChalkakSpacing.md)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
