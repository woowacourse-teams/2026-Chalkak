import SwiftUI

struct FeedPhoto: View {
    let post: FeedPost
    var isLikeEnabled: Bool = true
    let onLike: () -> Void
    @State private var imageRatio: CGFloat?

    var body: some View {
        VStack(spacing: 0) {
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(1 / (imageRatio ?? Metrics.defaultImageRatio), contentMode: .fit)
                .overlay {
                    ChalkakSignedImage(
                        imageSource: post.originalImageSource,
                        signatureSource: post.signatureImageSource,
                        contentDescription: post.contentDescription,
                        contentMode: .fill,
                        signatureSize: Metrics.signatureSize
                    )
                }
                .clipped()
                .task(id: post.originalImageSource) {
                    imageRatio = nil
                    imageRatio = await ImageRatioLoader.ratio(for: post.originalImageSource)
                }

            FeedLikeRow(
                likeCount: post.likeCount,
                isLiked: post.isLiked,
                isEnabled: isLikeEnabled,
                onLike: onLike
            )
        }
    }
}

private struct FeedLikeRow: View {
    @Environment(\.chalkakTheme) private var theme
    let likeCount: Int
    let isLiked: Bool
    let isEnabled: Bool
    let onLike: () -> Void

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: Metrics.rowHeight)
            .overlay(alignment: .topLeading) {
                HStack(spacing: Metrics.spacing - Metrics.heartTouchInset) {
                    Button(action: onLike) {
                        Image(isLiked ? "ic_heart_filled" : "ic_heart")
                            .renderingMode(.template)
                            .resizable()
                            .frame(width: Metrics.heartSize, height: Metrics.heartSize)
                            .foregroundStyle(
                                isLiked ? theme.colors.actionPrimary : theme.colors.iconSecondary
                            )
                            .padding(Metrics.heartTouchInset)
                            .contentShape(Rectangle())
                            .accessibilityHidden(true)
                    }
                    .buttonStyle(.plain)
                    .disabled(!isEnabled)
                    .accessibilityLabel("좋아요 \(likeCount)")
                    .accessibilityValue(isLiked ? "선택됨" : "")

                    Text("\(likeCount)")
                        .font(theme.typography.body)
                        .fontWeight(.regular)
                        .foregroundStyle(theme.colors.textSecondary)
                }
                // 하트와 숫자 위치는 유지하고 하트 주변만 터치할 수 있게 한다.
                .padding(.top, Metrics.topInset - Metrics.heartTouchInset)
                .padding(.leading, Metrics.horizontalInset - Metrics.heartTouchInset)
            }
    }
}

private enum Metrics {
    static let defaultImageRatio: CGFloat = 1
    static let signatureSize = CGSize(width: 70, height: 52)
    static let rowHeight: CGFloat = 60
    static let spacing: CGFloat = 9
    static let heartSize: CGFloat = 28
    static let minimumTouchSize: CGFloat = 44
    static let heartTouchInset = (minimumTouchSize - heartSize) / 2
    static let topInset: CGFloat = 22
    static let horizontalInset: CGFloat = 18
}

#Preview("Feed Photo", traits: .sizeThatFitsLayout) {
    FeedPhoto(
        post: FeedPreviewData.content.post,
        onLike: {}
    )
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
