import SwiftUI

struct DisplayFeaturedCarousel: View {
    @Environment(\.chalkakTheme) private var theme
    let photos: [DisplayPhoto]
    let currentPage: Int
    let onPageChange: (Int) -> Void

    @State private var selection = 0

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            TabView(selection: $selection) {
                ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                    DisplayFeaturedCard(photo: photo)
                        .padding(.horizontal, theme.spacing.xs)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .aspectRatio(Metrics.aspectRatio, contentMode: .fit)

            pageIndicator
        }
        .onChange(of: selection) { _, newValue in
            onPageChange(newValue)
        }
        .onAppear {
            selection = min(currentPage, max(0, photos.count - 1))
        }
    }

    private var pageIndicator: some View {
        HStack(spacing: Metrics.dotSpacing) {
            ForEach(photos.indices, id: \.self) { index in
                Circle()
                    .fill(
                        index == selection
                            ? theme.colors.actionPrimary
                            : theme.colors.textInactive.opacity(Metrics.inactiveDotOpacity)
                    )
                    .frame(width: Metrics.dotSize, height: Metrics.dotSize)
            }
        }
        .accessibilityHidden(true)
    }
}

private struct DisplayFeaturedCard: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: DisplayPhoto

    var body: some View {
        ChalkakSignedImage(
            imageSource: photo.originalImageSource,
            signatureSource: photo.signatureOriginalImageSource,
            contentDescription: photo.contentDescription,
            contentMode: .fill
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
        .overlay(alignment: .bottomLeading) {
            caption
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var caption: some View {
        let title = photo.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        HStack(spacing: theme.spacing.sm) {
            Image(systemName: "heart.fill")
                .font(.system(size: Metrics.heartSize))
                .accessibilityHidden(true)

            Text("\(photo.likeCount)")
                .font(theme.typography.footnote)

            if let title, !title.isEmpty {
                Text(title)
                    .font(theme.typography.footnote)
                    .lineLimit(1)
            }
        }
        .foregroundStyle(theme.colors.textOnImage)
        .padding(.horizontal, theme.spacing.md)
        .padding(.vertical, theme.spacing.sm)
        .background(theme.colors.scrim, in: Capsule())
        .padding(theme.spacing.md)
    }
}

private enum Metrics {
    static let aspectRatio: CGFloat = 4.0 / 5.0
    static let dotSpacing: CGFloat = 6
    static let dotSize: CGFloat = 6
    static let inactiveDotOpacity: CGFloat = 0.6
    static let heartSize: CGFloat = 13
}

#Preview("Featured Carousel") {
    DisplayFeaturedCarousel(
        photos: DisplayPreviewData.archiveState.featuredPhotos,
        currentPage: 0,
        onPageChange: { _ in }
    )
    .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
