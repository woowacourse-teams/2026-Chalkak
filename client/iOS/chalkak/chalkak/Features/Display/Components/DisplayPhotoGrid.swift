import SwiftUI

struct DisplayPhotoGrid: View {
    @Environment(\.chalkakTheme) private var theme
    let photos: [DisplayPhoto]
    let isLoadingNext: Bool
    let onEndThreshold: (Bool) -> Void

    private var columns: [GridItem] {
        Array(
            repeating: GridItem(spacing: Metrics.itemSpacing, alignment: .top),
            count: Metrics.columnCount
        )
    }

    var body: some View {
        LazyVGrid(columns: columns, spacing: Metrics.itemSpacing) {
            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                DisplayPhotoGridCell(photo: photo)
                    .onAppear {
                        onEndThreshold(index >= photos.count - Metrics.endThreshold)
                    }
            }
        }
        .overlay(alignment: .bottom) {
            if isLoadingNext {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .padding(.top, theme.spacing.xl)
                    .offset(y: Metrics.loadingOffset)
                    .accessibilityIdentifier("display-next-loading")
            }
        }
        .onAppear {
            onEndThreshold(false)
        }
    }
}

private struct DisplayPhotoGridCell: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: DisplayPhoto

    var body: some View {
        ChalkakSignedImage(
            imageSource: photo.thumbnailImageSource,
            signatureSource: photo.signatureThumbnailImageSource,
            contentDescription: photo.contentDescription,
            contentMode: .fill,
            signatureSize: Metrics.signatureSize
        )
        .aspectRatio(Metrics.aspectRatio, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
        .overlay(alignment: .bottomLeading) {
            likeBadge
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(photo.contentDescription), 좋아요 \(photo.likeCount)")
    }

    private var likeBadge: some View {
        HStack(spacing: Metrics.badgeSpacing) {
            Image(systemName: "heart.fill")
                .font(.system(size: Metrics.heartSize))
                .accessibilityHidden(true)

            Text("\(photo.likeCount)")
                .font(theme.typography.caption)
        }
        .foregroundStyle(theme.colors.textOnImage)
        .padding(.horizontal, Metrics.badgeHorizontalPadding)
        .padding(.vertical, Metrics.badgeVerticalPadding)
        .background(theme.colors.scrim, in: Capsule())
        .padding(Metrics.badgeInset)
    }
}

private enum Metrics {
    static let columnCount = 2
    static let itemSpacing: CGFloat = 12
    static let aspectRatio: CGFloat = 3.0 / 4.0
    static let endThreshold = 4
    static let loadingOffset: CGFloat = 44
    static let signatureSize = CGSize(width: 40, height: 30)
    static let heartSize: CGFloat = 11
    static let badgeSpacing: CGFloat = 4
    static let badgeHorizontalPadding: CGFloat = 8
    static let badgeVerticalPadding: CGFloat = 4
    static let badgeInset: CGFloat = 8
}

#Preview("Display Photo Grid") {
    ScrollView {
        DisplayPhotoGrid(
            photos: DisplayPreviewData.latestState.photos,
            isLoadingNext: true,
            onEndThreshold: { _ in }
        )
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
    }
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
