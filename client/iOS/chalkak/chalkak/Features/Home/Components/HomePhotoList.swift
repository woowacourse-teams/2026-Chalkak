import SwiftUI

struct HomePhotoList: View {
    @Environment(\.chalkakTheme) private var theme
    let photos: [HomePhoto]
    let likedPhotoIDs: Set<HomePhoto.ID>
    let isLoadingNext: Bool
    let areLikesEnabled: Bool
    var topContentPadding: CGFloat = 0
    var bottomContentPadding: CGFloat = 0
    let onLike: (HomePhoto.ID) -> Void
    let onEndThreshold: (Bool) -> Void
    var onSelect: (HomePhoto) -> Void = { _ in }

    var body: some View {
        LazyVStack(spacing: theme.spacing.xxl) {
            Color.clear
                .frame(height: topContentPadding)
                .accessibilityHidden(true)

            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                HomePhotoCard(
                    photo: photo,
                    isLiked: likedPhotoIDs.contains(photo.id),
                    isLikeEnabled: areLikesEnabled,
                    onLike: { onLike(photo.id) },
                    onSelect: { onSelect(photo) }
                )
                .onAppear {
                    onEndThreshold(index >= photos.count - HomePhotoListMetrics.endThreshold)
                }
            }

            if isLoadingNext {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(theme.spacing.xl)
                    .accessibilityIdentifier("home-next-loading")
            }

            Color.clear
                .frame(height: bottomContentPadding)
                .accessibilityHidden(true)
        }
        .padding(.bottom, theme.spacing.xxl + theme.spacing.sm)
        .onAppear {
            onEndThreshold(false)
        }
    }
}

private enum HomePhotoListMetrics {
    static let endThreshold = 3
}

#Preview("Home Photo List") {
    ScrollView {
        HomePhotoList(
            photos: HomePreviewData.contentState.photos,
            likedPhotoIDs: HomePreviewData.contentState.likedPhotoIDs,
            isLoadingNext: true,
            areLikesEnabled: true,
            onLike: { _ in },
            onEndThreshold: { _ in }
        )
    }
    .chalkakTheme(.light)
}
