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
            Button(action: onBack) {
                Image(systemName: "arrow.backward")
                    .font(.system(size: Metrics.iconSize))
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")

            Spacer(minLength: 0)

            if isEditVisible || isDeleteVisible {
                HStack(spacing: 0) {
                    if isEditVisible {
                        actionButton(
                            title: "수정",
                            color: theme.colors.actionPrimary,
                            isEnabled: isEditEnabled,
                            action: onEdit
                        )
                    }
                    if isDeleteVisible {
                        actionButton(
                            title: "삭제",
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
        color: Color,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(theme.typography.callout)
                .foregroundStyle(color)
                .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(title)
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 24
    static let touchSize: CGFloat = 44
}

#Preview("Feed Top Bar", traits: .sizeThatFitsLayout) {
    FeedTopBar(onBack: {})
        .padding(.horizontal, 8)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
