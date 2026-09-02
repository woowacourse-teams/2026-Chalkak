import SwiftUI

struct DisplayFeaturedCarousel: View {
    @Environment(\.chalkakTheme) private var theme
    let photos: [DisplayPhoto]
    let currentPage: Int
    let onPageChange: (Int) -> Void

    @State private var scrollID: Int?

    var body: some View {
        VStack(spacing: theme.spacing.lg) {
            ScrollView(.horizontal) {
                LazyHStack(spacing: Metrics.pageSpacing) {
                    ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                        DisplayFeaturedCard(photo: photo)
                            .containerRelativeFrame(.horizontal)
                            .scrollTransition(.interactive, axis: .horizontal) { content, phase in
                                content
                                    .scaleEffect(1 - CGFloat(min(abs(phase.value), 1)) * Metrics.scaleFalloff)
                                    .opacity(1 - min(abs(phase.value), 1) * Double(Metrics.opacityFalloff))
                            }
                            .id(index)
                    }
                }
                .scrollTargetLayout()
            }
            .contentMargins(.horizontal, Metrics.peekMargin, for: .scrollContent)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $scrollID, anchor: .center)
            .scrollIndicators(.hidden)
            .padding(.horizontal, Metrics.hardMargin)

            DisplayPageIndicator(pageCount: photos.count, selectedPage: selectedPage)
        }
        .onAppear {
            if scrollID == nil {
                scrollID = currentPage.coerced(to: 0...max(0, photos.count - 1))
            }
        }
        .onChange(of: scrollID) { _, newValue in
            guard let newValue else { return }
            onPageChange(newValue)
        }
    }

    private var selectedPage: Int {
        (scrollID ?? currentPage).coerced(to: 0...max(0, photos.count - 1))
    }
}

private struct DisplayFeaturedCard: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: DisplayPhoto

    var body: some View {
        ZStack {
            Color.black

            ChalkakSignedImage(
                imageSource: photo.originalImageSource,
                signatureSource: photo.signatureOriginalImageSource,
                contentDescription: photo.contentDescription,
                contentMode: .fit,
                signatureSize: Metrics.signatureSize
            )
        }
        .aspectRatio(Metrics.aspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
        .overlay(alignment: .bottomLeading) {
            DisplayLikeBadge(likeCount: photo.likeCount)
                .padding(Metrics.badgeInset)
        }
        .overlay(alignment: .bottom) {
            if let title = photo.title?.trimmingCharacters(in: .whitespacesAndNewlines),
               !title.isEmpty {
                Text(title)
                    .font(theme.typography.handwriting)
                    .foregroundStyle(theme.colors.textOnImage)
                    .lineLimit(1)
                    .padding(.bottom, Metrics.titleBottomPadding)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(photo.contentDescription), 좋아요 \(photo.likeCount)")
    }
}

struct DisplayPageIndicator: View {
    @Environment(\.chalkakTheme) private var theme
    let pageCount: Int
    let selectedPage: Int

    var body: some View {
        HStack(spacing: Metrics.dotSpacing) {
            ForEach(0..<pageCount, id: \.self) { page in
                Capsule()
                    .fill(
                        page == selectedPage
                            ? theme.colors.actionPrimary
                            : theme.colors.textMuted.opacity(Metrics.inactiveDotOpacity)
                    )
                    .frame(
                        width: page == selectedPage ? Metrics.selectedDotWidth : Metrics.dotSize,
                        height: Metrics.dotSize
                    )
            }
        }
        .accessibilityHidden(true)
    }
}

private enum Metrics {
    static let aspectRatio: CGFloat = 3.0 / 4.0
    static let pageSpacing: CGFloat = 5
    // Android DisplayFeaturedPager 기준: 그리드 좌우 여백(22) + 페이저 콘텐츠 패딩(32).
    static let hardMargin: CGFloat = 22
    static let peekMargin: CGFloat = 32
    static let scaleFalloff: CGFloat = 0.08
    static let opacityFalloff: CGFloat = 0.12
    static let signatureSize = CGSize(width: 48, height: 36)
    static let badgeInset: CGFloat = 10
    static let titleBottomPadding: CGFloat = 10
    static let dotSpacing: CGFloat = 8
    static let dotSize: CGFloat = 7
    static let selectedDotWidth: CGFloat = 24
    static let inactiveDotOpacity: CGFloat = 0.45
}

private extension Int {
    func coerced(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

#Preview("Featured Carousel") {
    DisplayFeaturedCarousel(
        photos: DisplayPreviewData.archiveState.featuredPhotos,
        currentPage: 0,
        onPageChange: { _ in }
    )
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
