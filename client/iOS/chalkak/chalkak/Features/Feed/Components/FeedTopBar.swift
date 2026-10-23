import SwiftUI

struct FeedTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    @ScaledMetric(relativeTo: .body) private var liquidGlassActionIconSize = Metrics.liquidGlassActionIconSize
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
                .frame(width: actionIconSize, height: actionIconSize)
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
            .contentShape(Circle())
            .disabled(!isEnabled)
            .accessibilityLabel(title)
    }

    private var actionIconSize: CGFloat {
        ChalkakPlatformAppearance.usesLiquidGlass ? liquidGlassActionIconSize : Metrics.actionIconSize
    }
}

private enum Metrics {
    static let actionIconSize: CGFloat = 24
    // 네이티브 뒤로가기 아이콘과 같은 크기로 글라스 버튼의 내부 높이를 맞춘다.
    static let liquidGlassActionIconSize: CGFloat = 17
}

#Preview("Feed Top Bar", traits: .sizeThatFitsLayout) {
    FeedTopBar(onBack: {}, isEditVisible: true, isDeleteVisible: true)
        .padding(.horizontal, ChalkakSpacing.lg)
        .padding(.vertical, ChalkakSpacing.md)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
