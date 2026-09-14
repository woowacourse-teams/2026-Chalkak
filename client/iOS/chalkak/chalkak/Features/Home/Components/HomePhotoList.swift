import SwiftUI

struct HomePhotoList: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var imageRatios: [ChalkakImageSource: CGFloat] = [:]
    let photos: [HomePhoto]
    let likedPhotoIDs: Set<HomePhoto.ID>
    let areLikesEnabled: Bool
    let imageReloadGeneration: Int
    var topContentPadding: CGFloat = 0
    var bottomContentPadding: CGFloat = 0
    let onLike: (HomePhoto.ID) -> Void
    let onEndThreshold: (Bool) -> Void

    var body: some View {
        LazyVStack(spacing: theme.spacing.xxl) {
            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                HomePhotoCard(
                    photo: photo,
                    isLiked: likedPhotoIDs.contains(photo.id),
                    isLikeEnabled: areLikesEnabled,
                    imageReloadGeneration: imageReloadGeneration,
                    imageRatio: imageRatios[photo.imageSource],
                    onImageRatioChanged: { ratio in
                        updateImageRatio(ratio, for: photo.imageSource)
                    },
                    onLike: { onLike(photo.id) }
                )
                .onAppear {
                    guard index == endThresholdIndex else { return }
                    onEndThreshold(true)
                }
            }

            Color.clear
                .frame(height: bottomContentPadding)
                .accessibilityHidden(true)
        }
        .scrollTargetLayout()
        .padding(.top, topContentPadding)
        .padding(.bottom, theme.spacing.xxl + theme.spacing.sm)
        .onAppear {
            onEndThreshold(false)
        }
    }

    private var endThresholdIndex: Int {
        max(photos.count - HomePhotoListMetrics.endThreshold, 0)
    }

    private func updateImageRatio(_ ratio: CGFloat?, for source: ChalkakImageSource) {
        guard imageRatios[source] != ratio else { return }
        var transaction = Transaction()
        transaction.animation = nil
        withTransaction(transaction) {
            imageRatios[source] = ratio
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
            areLikesEnabled: true,
            imageReloadGeneration: 0,
            onLike: { _ in },
            onEndThreshold: { _ in }
        )
    }
    .chalkakTheme(.light)
}
