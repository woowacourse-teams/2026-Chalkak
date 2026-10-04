import SwiftUI

struct FeedPhoto: View {
    // 상세를 받기 전에는 nil이며, 이때는 placeholder만 보여준다.
    let post: FeedPost?
    let placeholder: FeedPhotoPlaceholder?
    let isLikeEnabled: Bool
    let zoom: FeedPhotoZoom
    let onLike: () -> Void
    @State private var imageRatio: CGFloat?
    @State private var imageFrame: CGRect?
    @State private var isOriginalLoaded = false

    init(
        post: FeedPost?,
        placeholder: FeedPhotoPlaceholder? = nil,
        isLikeEnabled: Bool = true,
        zoom: FeedPhotoZoom = FeedPhotoZoom(),
        onLike: @escaping () -> Void
    ) {
        self.post = post
        self.placeholder = placeholder
        self.isLikeEnabled = isLikeEnabled
        self.zoom = zoom
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
                        FeedPlaceholderPhoto(placeholder: placeholder, signatureSize: Metrics.signatureSize)
                    }
                }
                .overlay {
                    if let post {
                        // placeholder가 있으면 원본은 로드된 뒤 그 위로 페이드되므로 스켈레톤은 띄우지 않는다.
                        ChalkakSignedImage(
                            imageSource: post.originalImageSource,
                            signatureSource: post.signatureImageSource,
                            contentDescription: post.contentDescription,
                            contentMode: .fill,
                            signatureSize: Metrics.signatureSize,
                            showsLoadingSkeleton: placeholder == nil,
                            onImageLoad: { isOriginalLoaded = true }
                        )
                        .opacity(isOriginalRevealed ? 1 : 0)
                        .animation(Metrics.originalRevealAnimation, value: isOriginalRevealed)
                    }
                }
                .clipped()
                .feedZoomEffect(
                    progress: zoom.photoProgress,
                    source: zoom.source,
                    destinationFrame: imageFrame
                )
                .opacity(photoOpacity)
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
                .opacity(zoom.contentProgress)
            }
        }
    }

    // Android SharedFeedImage와 같이, 출발 이미지가 있으면 원본은 로드됐고 열림 전환이 끝난 뒤에만 드러낸다.
    private var isOriginalRevealed: Bool {
        placeholder == nil || (isOriginalLoaded && zoom.isSettled)
    }

    private var photoOpacity: CGFloat {
        zoom.source == nil ? zoom.contentProgress : 1
    }
}

/// 출발 화면에 떠 있던 사진과 서명. 미리 받아 둔 이미지가 있으면 로딩 없이 바로 그린다.
private struct FeedPlaceholderPhoto: View {
    @Environment(\.chalkakTheme) private var theme
    let placeholder: FeedPhotoPlaceholder
    let signatureSize: CGSize

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottomTrailing) {
                image(placeholder.preloadedImage, source: placeholder.imageSource, contentMode: .fill)
                    .frame(width: proxy.size.width, height: proxy.size.height)
                    .clipped()

                if let signatureSource = placeholder.signatureImageSource {
                    image(placeholder.preloadedSignatureImage, source: signatureSource, contentMode: .fit)
                        .frame(width: signatureSize.width, height: signatureSize.height)
                        .padding(theme.spacing.sm)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func image(
        _ preloaded: UIImage?,
        source: ChalkakImageSource,
        contentMode: ContentMode
    ) -> some View {
        if let preloaded {
            Image(uiImage: preloaded)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        } else {
            ChalkakImage(source: source, contentDescription: nil, contentMode: contentMode)
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
    // Android PHOTO_CROSSFADE_DURATION_MILLIS(180) + tween 기본 FastOutSlowInEasing.
    static let originalRevealAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.18)
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
