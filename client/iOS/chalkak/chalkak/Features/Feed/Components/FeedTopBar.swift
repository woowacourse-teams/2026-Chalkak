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
                            systemImage: "pencil",
                            color: theme.colors.actionPrimary,
                            isEnabled: isEditEnabled,
                            action: onEdit
                        )
                    }
                    if isDeleteVisible {
                        actionButton(
                            title: "삭제",
                            systemImage: "trash",
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
        systemImage: String,
        color: Color,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(title, systemImage: systemImage, action: action)
            .labelStyle(.iconOnly)
            .tint(color)
            .controlSize(.large)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .frame(
                width: ChalkakNavigationButton.diameter,
                height: ChalkakNavigationButton.diameter
            )
            .disabled(!isEnabled)
            .accessibilityLabel(title)
    }
}

#Preview("Feed Top Bar", traits: .sizeThatFitsLayout) {
    FeedTopBar(onBack: {}, isEditVisible: true, isDeleteVisible: true)
        .padding(.horizontal, ChalkakSpacing.lg)
        .padding(.vertical, ChalkakSpacing.md)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
