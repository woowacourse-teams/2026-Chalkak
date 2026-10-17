import SwiftUI

/// 사진 앱 편집 화면처럼 하단에서 필터·자르기 탭을 고른다.
struct PhotoEditModeBar: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedMode: PhotoEditMode
    let onSelect: (PhotoEditMode) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(PhotoEditMode.allCases) { mode in
                item(mode)
            }
        }
        .padding(.horizontal, theme.spacing.sm)
        .padding(.vertical, theme.spacing.xs)
        .background(theme.colors.editorControl, in: Capsule())
    }

    private func item(_ mode: PhotoEditMode) -> some View {
        let isSelected = mode == selectedMode

        return Button {
            onSelect(mode)
        } label: {
            VStack(spacing: theme.spacing.xs) {
                Image(systemName: mode.systemImage)
                    .font(.system(size: Metrics.iconSize))
                    .accessibilityHidden(true)
                Text(mode.title)
                    .font(theme.typography.caption)
            }
            .foregroundStyle(isSelected ? theme.colors.textOnEditor : theme.colors.textOnEditorMuted)
            .frame(minWidth: Metrics.itemWidth, minHeight: Metrics.itemHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(mode.title)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("photoEdit.mode.\(mode)")
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 22
    static let itemWidth: CGFloat = 72
    static let itemHeight: CGFloat = 52
}

#Preview("Photo Edit Mode Bar") {
    PhotoEditModeBar(selectedMode: .crop, onSelect: { _ in })
        .padding(ChalkakSpacing.lg)
        .background(ChalkakColors.light.editorBackground)
        .chalkakTheme(.light)
}
