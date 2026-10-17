import SwiftUI
import UIKit

struct PhotoEditFilterStrip: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedFilter: PhotoEditFilter
    let thumbnails: [PhotoEditFilter: UIImage]
    let onSelect: (PhotoEditFilter) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: theme.spacing.md) {
                ForEach(PhotoEditFilter.allCases) { filter in
                    item(filter)
                }
            }
        }
    }

    private func item(_ filter: PhotoEditFilter) -> some View {
        let isSelected = filter == selectedFilter

        return Button {
            onSelect(filter)
        } label: {
            VStack(spacing: theme.spacing.xs) {
                thumbnail(filter)
                    .frame(width: Metrics.thumbnailSize, height: Metrics.thumbnailSize)
                    .clipShape(RoundedRectangle(cornerRadius: theme.shapes.small))
                    .overlay {
                        RoundedRectangle(cornerRadius: theme.shapes.small)
                            .strokeBorder(
                                isSelected ? theme.colors.textOnEditor : .clear,
                                lineWidth: Metrics.selectedBorderWidth
                            )
                    }

                Text(filter.title)
                    .font(theme.typography.caption)
                    .foregroundStyle(isSelected ? theme.colors.textOnEditor : theme.colors.textOnEditorMuted)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(filter.title) 필터")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("photoEdit.filter.\(filter)")
    }

    @ViewBuilder
    private func thumbnail(_ filter: PhotoEditFilter) -> some View {
        if let image = thumbnails[filter] {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .accessibilityHidden(true)
        } else {
            Rectangle()
                .fill(theme.colors.textOnEditorMuted.opacity(Metrics.placeholderOpacity))
                .accessibilityHidden(true)
        }
    }
}

private enum Metrics {
    static let thumbnailSize: CGFloat = 64
    static let selectedBorderWidth: CGFloat = 2
    static let placeholderOpacity: Double = 0.3
}

#Preview("Photo Edit Filter Strip") {
    PhotoEditFilterStrip(
        selectedFilter: .mono,
        thumbnails: [:],
        onSelect: { _ in }
    )
    .contentMargins(.horizontal, ChalkakSpacing.lg, for: .scrollContent)
    .padding(.vertical, ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
