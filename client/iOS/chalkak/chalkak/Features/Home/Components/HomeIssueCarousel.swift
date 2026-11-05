import SwiftUI

/// ISSUE를 가로로 넘겨 보는 캐러셀. 넘길 수 있다는 걸 알 수 있도록 다음 카드를 `Metrics.cardPeek`만큼 보여 준다.
struct HomeIssueCarousel: View {
    @Environment(\.chalkakTheme) private var theme
    let banners: [Banner]
    let isLoadingMore: Bool
    let hasLoadMoreError: Bool
    var onIssueClick: (Banner) -> Void
    var onRetryLoadMore: () -> Void
    var onBannerVisibilityChange: (Banner.ID, Bool) -> Void
    var onSwipe: (Int) -> Void = { _ in }

    @State private var containerWidth: CGFloat = 0
    @State private var currentBannerID: Banner.ID?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: theme.spacing.md) {
                ForEach(banners) { banner in
                    HomeIssueSection(
                        banner: banner,
                        onIssueClick: { _ in onIssueClick(banner) },
                        showHeader: false,
                        textLineLimit: Metrics.textLineLimit
                    )
                    .frame(width: cardWidth)
                    .onScrollVisibilityChange(threshold: 0.01) { isVisible in
                        onBannerVisibilityChange(banner.id, isVisible)
                    }
                }

                if showsPaginationCard {
                    paginationCard
                        .frame(width: cardWidth)
                }
            }
            .scrollTargetLayout()
        }
        .contentMargins(.leading, theme.spacing.screenHorizontal, for: .scrollContent)
        .contentMargins(.trailing, trailingMargin, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $currentBannerID)
        .scrollDisabled(cardCount <= 1)
        .onChange(of: currentBannerID) { previous, current in
            guard let index = banners.firstIndex(where: { $0.id == current }),
                  previous != nil || index > 0 else { return }
            onSwipe(index)
        }
        .onGeometryChange(for: CGFloat.self) { geometry in
            geometry.size.width
        } action: { width in
            containerWidth = width
        }
    }

    private var showsPaginationCard: Bool {
        isLoadingMore || hasLoadMoreError
    }

    private var cardCount: Int {
        banners.count + (showsPaginationCard ? 1 : 0)
    }

    private var trailingMargin: CGFloat {
        cardCount > 1 ? theme.spacing.md + Metrics.cardPeek : theme.spacing.screenHorizontal
    }

    private var cardWidth: CGFloat {
        max(containerWidth - theme.spacing.screenHorizontal - trailingMargin, 0)
    }

    private var paginationCard: some View {
        theme.colors.surfaceElevated
            .aspectRatio(HomeIssueSection.imageAspectRatio, contentMode: .fit)
            .overlay {
                if isLoadingMore {
                    ProgressView()
                        .tint(theme.colors.actionPrimary)
                        .accessibilityLabel("ISSUE 더 불러오는 중")
                        .accessibilityIdentifier("home.issue.loadingMore")
                } else {
                    Button(action: onRetryLoadMore) {
                        Text("다시 시도")
                            .font(theme.typography.subheadline)
                            .foregroundStyle(theme.colors.textPrimary)
                            .frame(minWidth: Metrics.minimumTouchSize, minHeight: Metrics.minimumTouchSize)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("home.issue.retry")
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
    }
}

private enum Metrics {
    static let cardPeek: CGFloat = 32
    // 카드 높이가 페이지마다 달라지지 않도록 제목과 부제를 한 줄로 고정한다.
    static let textLineLimit = 1
    static let minimumTouchSize: CGFloat = 44
}

#Preview("Issue Carousel", traits: .sizeThatFitsLayout) {
    HomeIssueCarousel(
        banners: (0..<3).map { index in
            Banner(
                id: "preview-banner-\(index)",
                title: HomePreviewData.banner.title,
                subtitle: HomePreviewData.banner.subtitle,
                webViewURL: HomePreviewData.banner.webViewURL,
                thumbnailURL: HomePreviewData.banner.thumbnailURL,
                createdAt: HomePreviewData.banner.createdAt
            )
        },
        isLoadingMore: false,
        hasLoadMoreError: false,
        onIssueClick: { _ in },
        onRetryLoadMore: {},
        onBannerVisibilityChange: { _, _ in }
    )
    .chalkakTheme(.light)
}
