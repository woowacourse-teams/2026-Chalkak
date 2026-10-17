import SwiftUI

/// 편집 첫 화면 하단에서 들어갈 도구를 고른다.
struct PhotoEditToolPicker: View {
    @Environment(\.chalkakTheme) private var theme

    let onSelect: (PhotoEditTool) -> Void

    var body: some View {
        HStack(spacing: theme.spacing.sm) {
            ForEach(PhotoEditTool.allCases) { tool in
                Button {
                    onSelect(tool)
                } label: {
                    Image(systemName: tool.systemImage)
                        .font(.system(size: Metrics.iconSize))
                        .foregroundStyle(theme.colors.textOnEditor)
                        .frame(width: Metrics.itemSize, height: Metrics.itemSize)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tool.title)
                .accessibilityIdentifier("photoEdit.tool.\(tool)")
            }
        }
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.xs)
        .background(theme.colors.editorControl, in: Capsule())
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 22
    static let itemSize: CGFloat = 52
}

#Preview("Photo Edit Tool Picker") {
    PhotoEditToolPicker(onSelect: { _ in })
        .padding(ChalkakSpacing.lg)
        .background(ChalkakColors.light.editorBackground)
        .chalkakTheme(.light)
}
