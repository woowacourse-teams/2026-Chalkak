import SwiftUI

struct HomeRankingRow: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    let ranking: HomeRankingState
    let displayDate: Date
    var onOpenDisplay: (Date) -> Void
    let zoomSource: (DisplayPhoto.ID) -> FeedZoomSource
    var onOpenFeed: (DisplayPhoto, FeedZoomSource) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            HomeSectionHeader(
                title: title,
                suffix: nil,
                moreContentDescription: "\(title) 더보기",
                onMoreClick: { onOpenDisplay(displayDate) }
            )

            switch ranking.status {
            case .loading:
                HomeStatusRow {
                    ProgressView()
                        .tint(theme.colors.actionPrimary)
                }
            case .error:
                RankingMessage(text: "\(title) 사진을 불러오지 못했어요")
            case .ready:
                if ranking.photos.isEmpty {
                    RankingMessage(text: "아직 올라온 사진이 없어요")
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: theme.spacing.lg) {
                            ForEach(Array(ranking.photos.prefix(Metrics.previewCount))) { photo in
                                RankingPhotoCard(
                                    photo: photo,
                                    zoomSource: zoomSource(photo.id),
                                    onClick: { onOpenFeed(photo, zoomSource(photo.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct RankingPhotoCard: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: DisplayPhoto
    let zoomSource: FeedZoomSource
    var onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            ChalkakImage(
                source: photo.thumbnailImageSource,
                contentDescription: nil
            )
            .frame(width: Metrics.itemWidth, height: Metrics.itemWidth)
            .background(theme.colors.calendarCell)
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.small))
            .feedZoomSource(zoomSource, cornerRadius: theme.shapes.small)
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.small))
        }
        .buttonStyle(ChalkakUnhighlightedButtonStyle())
        .accessibilityLabel(photo.contentDescription)
    }
}

private struct RankingMessage: View {
    @Environment(\.chalkakTheme) private var theme
    let text: String

    var body: some View {
        HomeStatusRow {
            Text(text)
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.textSecondary)
        }
    }
}

private enum Metrics {
    static let previewCount = 5
    static let itemWidth: CGFloat = 82
}

#Preview("Ranking Row", traits: .sizeThatFitsLayout) {
    HomeRankingRow(
        title: "어제 랭킹",
        ranking: HomePreviewData.contentState.yesterday,
        displayDate: HomePreviewData.previewDate,
        onOpenDisplay: { _ in },
        zoomSource: FeedZoomSource.homeYesterday,
        onOpenFeed: { _, _ in }
    )
    .chalkakTheme(.light)
}
