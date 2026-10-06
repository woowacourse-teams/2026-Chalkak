import SwiftUI

struct FeedPhoto: View {
    @Environment(\.chalkakTheme) private var theme
    // 상세를 받기 전에는 nil이며, 이때는 placeholder만 보여준다.
    let post: FeedPost?
    let placeholder: FeedPhotoPlaceholder?
    let isLikeEnabled: Bool
    let zoom: FeedPhotoZoom
    let onLike: () -> Void
    @State private var imageRatio: CGFloat?
    @State private var imageFrame: CGRect?
    @State private var signatureFrame: CGRect?
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
                        FeedPlaceholderImage(
                            preloaded: placeholder.preloadedImage,
                            source: placeholder.imageSource,
                            contentMode: .fill
                        )
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
                // Android처럼 출발 화면의 서명은 사진과 따로, 출발 서명 위치·크기에서 제자리로 이동한다.
                .overlay(alignment: .bottomTrailing) {
                    if let placeholder, let signatureSource = placeholder.signatureImageSource {
                        FeedPlaceholderImage(
                            preloaded: placeholder.preloadedSignatureImage,
                            source: signatureSource,
                            contentMode: .fit
                        )
                        .frame(width: Metrics.signatureSize.width, height: Metrics.signatureSize.height)
                        .feedZoomEffect(
                            progress: zoom.photoProgress,
                            source: signatureZoomSource,
                            destinationFrame: signatureFrame
                        )
                        .onGeometryChange(for: CGRect.self) { proxy in
                            proxy.frame(in: .global)
                        } action: { frame in
                            signatureFrame = frame
                        }
                        .opacity(signatureOpacity)
                        .padding(theme.spacing.sm)
                    }
                }
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

    private var signatureZoomSource: FeedZoomRegistry.Source? {
        zoom.source?.signatureFrame.map { FeedZoomRegistry.Source(frame: $0, cornerRadius: 0) }
    }

    // 사진은 날아가는데 출발 서명 위치를 모르면, 서명은 제자리에서 다른 콘텐츠와 함께 페이드된다.
    private var signatureOpacity: CGFloat {
        zoom.source != nil && signatureZoomSource == nil ? zoom.contentProgress : 1
    }
}

/// 출발 화면에 떠 있던 이미지. 미리 받아 둔 이미지가 있으면 로딩 없이 바로 그린다.
private struct FeedPlaceholderImage: View {
    let preloaded: UIImage?
    let source: ChalkakImageSource
    let contentMode: ContentMode

    var body: some View {
        Group {
            if let preloaded {
                Image(uiImage: preloaded)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                ChalkakImage(source: source, contentDescription: nil, contentMode: contentMode)
            }
        }
        .accessibilityHidden(true)
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
    // Android PHOTO_CROSSFADE_DURATION_MILLIS(180) + tween 기본 FastOutSlowInEasing.
    static let originalRevealAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.18)
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
