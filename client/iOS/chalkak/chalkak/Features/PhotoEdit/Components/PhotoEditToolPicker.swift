import SwiftUI

/// 편집 첫 화면 하단에서 들어갈 도구를 고른다. 아이콘 아래에 도구 이름을 함께 보여 준다.
struct PhotoEditToolPicker: View {
    @Environment(\.chalkakTheme) private var theme

    let onSelect: (PhotoEditTool) -> Void

    var body: some View {
        HStack(spacing: theme.spacing.sm) {
            ForEach(PhotoEditTool.allCases) { tool in
                Button {
                    onSelect(tool)
                } label: {
                    VStack(spacing: theme.spacing.xs) {
                        Image(systemName: tool.systemImage)
                            .font(.system(size: Metrics.iconSize))
                            .frame(height: Metrics.iconFrameHeight)
                            .accessibilityHidden(true)
                        Text(tool.title)
                            .font(theme.typography.caption)
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .foregroundStyle(theme.colors.textOnEditor)
                    .frame(minWidth: Metrics.itemMinWidth, minHeight: Metrics.itemMinHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tool.title)
                .accessibilityIdentifier("photoEdit.tool.\(tool)")
            }
        }
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.xs)
        .photoEditControlBackground(in: Capsule())
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 22
    static let iconFrameHeight: CGFloat = 28
    static let itemMinWidth: CGFloat = 64
    static let itemMinHeight: CGFloat = 56
}

#Preview("Photo Edit Tool Picker") {
    PhotoEditToolPicker(onSelect: { _ in })
        .padding(ChalkakSpacing.lg)
        .background(ChalkakColors.light.editorBackground)
        .chalkakTheme(.light)
}
