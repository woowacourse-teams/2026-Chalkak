import SwiftUI

struct FeedPhoto: View {
    // 상세를 받기 전에는 nil이며, 이때는 placeholder만 보여준다.
    let post: FeedPost?
    let placeholder: FeedPhotoPlaceholder?
    let isLikeEnabled: Bool
    // 줌 전환 진행도(0: 출발 뷰 위치, 1: 제자리)와 출발 뷰. 사진만 이동하고 나머지는 페이드된다.
    let zoomProgress: CGFloat
    let zoomSource: FeedZoomRegistry.Source?
    let onLike: () -> Void
    @State private var imageRatio: CGFloat?
    @State private var imageFrame: CGRect?

    init(
        post: FeedPost?,
        placeholder: FeedPhotoPlaceholder? = nil,
        isLikeEnabled: Bool = true,
        zoomProgress: CGFloat = 1,
        zoomSource: FeedZoomRegistry.Source? = nil,
        onLike: @escaping () -> Void
    ) {
        self.post = post
        self.placeholder = placeholder
        self.isLikeEnabled = isLikeEnabled
        self.zoomProgress = zoomProgress
        self.zoomSource = zoomSource
        self.onLike = onLike
        _imageRatio = State(initialValue: placeholder?.heightToWidthRatio)
    }

    var body: some View {
        VStack(spacing: 0) {
            Color.clear
                .frame(maxWidth: .infinity)
                .aspectRatio(1 / (imageRatio ?? Metrics.defaultImageRatio), contentMode: .fit)
                .overlay {
                    if let placeholder {
                        ChalkakImage(
                            source: placeholder.imageSource,
                            contentDescription: nil,
                            contentMode: .fill
                        )
                    }
                }
                .overlay {
                    if let post {
                        // placeholder가 있으면 원본이 로드될 때 그 위를 덮어 교체하므로 스켈레톤은 띄우지 않는다.
                        ChalkakSignedImage(
                            imageSource: post.originalImageSource,
                            signatureSource: post.signatureImageSource,
                            contentDescription: post.contentDescription,
                            contentMode: .fill,
                            signatureSize: Metrics.signatureSize,
                            showsLoadingSkeleton: placeholder == nil
                        )
                    }
                }
                .clipped()
                .feedZoomEffect(
                    progress: zoomProgress,
                    source: zoomSource,
                    destinationFrame: imageFrame
                )
                .onGeometryChange(for: CGRect.self) { proxy in
                    proxy.frame(in: .global)
                } action: { frame in
                    imageFrame = frame
                }
                .zIndex(1)
                .task(id: post?.originalImageSource) {
                    if imageRatio == nil, let placeholder {
                        imageRatio = await ImageRatioLoader.ratio(for: placeholder.imageSource)
                    }
                    guard let post,
                          let ratio = await ImageRatioLoader.ratio(for: post.originalImageSource)
                    else { return }
                    imageRatio = ratio
                }

            if let post {
                FeedLikeRow(
                    likeCount: post.likeCount,
                    isLiked: post.isLiked,
                    isEnabled: isLikeEnabled,
                    onLike: onLike
                )
                .opacity(zoomProgress)
            }
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
    static let defaultImageRatio: CGFloat = 1
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
