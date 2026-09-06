import SwiftUI

struct FeedPhoto: View {
    let post: FeedPost
    var isLikeEnabled: Bool = true
    let onLike: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(Metrics.photoAspectRatio, contentMode: .fit)
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
        Button(action: onLike) {
            // Android FeedLikeRow: height 60 고정 박스 안에서 start 18 / top 22 인셋으로 배치.
            Color.clear
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.rowHeight)
                .overlay(alignment: .topLeading) {
                    HStack(spacing: Metrics.spacing) {
                        Image(isLiked ? "ic_heart_filled" : "ic_heart")
                            .renderingMode(.template)
                            .resizable()
                            .frame(width: Metrics.heartSize, height: Metrics.heartSize)
                            .foregroundStyle(
                                isLiked ? theme.colors.actionPrimary : theme.colors.iconSecondary
                            )
                            .accessibilityHidden(true)

                        Text("\(likeCount)")
                            .font(theme.typography.body)
                            .fontWeight(.regular)
                            .foregroundStyle(theme.colors.textSecondary)
                    }
                    .padding(.top, Metrics.topInset)
                    .padding(.leading, Metrics.horizontalInset)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel("좋아요 \(likeCount)")
        .accessibilityValue(isLiked ? "선택됨" : "")
    }
}

private enum Metrics {
    static let photoAspectRatio: CGFloat = 0.935
    static let signatureSize = CGSize(width: 70, height: 52)
    static let rowHeight: CGFloat = 60
    static let spacing: CGFloat = 9
    static let heartSize: CGFloat = 28
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
