import SwiftUI

/// 비율 모양을 아이콘으로 보여 주는 자르기 비율 목록.
struct PhotoEditAspectRatioStrip: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedAspectRatio: PhotoEditAspectRatio
    /// 회전을 반영한 원본 크기. 원본 비율 아이콘 모양에 쓴다.
    let originalSize: CGSize
    let onSelect: (PhotoEditAspectRatio) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.lg) {
                ForEach(PhotoEditAspectRatio.allCases) { aspectRatio in
                    item(aspectRatio)
                }
            }
        }
    }

    private func item(_ aspectRatio: PhotoEditAspectRatio) -> some View {
        let isSelected = aspectRatio == selectedAspectRatio
        let color = isSelected ? theme.colors.editorAccent : theme.colors.textOnEditor

        return Button {
            onSelect(aspectRatio)
        } label: {
            VStack(spacing: theme.spacing.sm) {
                RoundedRectangle(cornerRadius: Metrics.iconCornerRadius)
                    .strokeBorder(color, lineWidth: Metrics.iconBorderWidth)
                    .frame(width: iconSize(aspectRatio).width, height: iconSize(aspectRatio).height)
                    .frame(width: Metrics.iconBox, height: Metrics.iconBox)
                    .accessibilityHidden(true)

                Text(aspectRatio.title)
                    .font(theme.typography.caption)
                    .foregroundStyle(color)
            }
            .frame(minWidth: Metrics.minimumTouchSize, minHeight: Metrics.minimumTouchSize)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(aspectRatio == .free ? "자유 비율" : "\(aspectRatio.title) 비율")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func iconSize(_ aspectRatio: PhotoEditAspectRatio) -> CGSize {
        // 자유 비율은 정해진 모양이 없어 세로 사각형으로 보여 준다.
        let ratio = aspectRatio.widthToHeight(originalSize: originalSize) ?? Metrics.freeIconRatio
        if ratio >= 1 {
            return CGSize(width: Metrics.iconLongEdge, height: Metrics.iconLongEdge / ratio)
        }
        return CGSize(width: Metrics.iconLongEdge * ratio, height: Metrics.iconLongEdge)
    }
}

private enum Metrics {
    static let iconBox: CGFloat = 36
    static let iconLongEdge: CGFloat = 32
    static let iconCornerRadius: CGFloat = 4
    static let iconBorderWidth: CGFloat = 1.5
    static let freeIconRatio: Double = 0.75
    static let minimumTouchSize: CGFloat = 44
}

#Preview("Photo Edit Aspect Ratio Strip") {
    PhotoEditAspectRatioStrip(
        selectedAspectRatio: .free,
        originalSize: CGSize(width: 3, height: 4),
        onSelect: { _ in }
    )
    .contentMargins(.horizontal, ChalkakSpacing.lg, for: .scrollContent)
    .padding(.vertical, ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
