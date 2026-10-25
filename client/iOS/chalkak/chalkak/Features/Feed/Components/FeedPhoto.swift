import SwiftUI

struct FeedPhoto: View {
    @Environment(\.chalkakTheme) private var theme
    // 상세를 받기 전에는 nil이며, 이때는 placeholder만 보여준다.
    let post: FeedPost?
    let placeholder: FeedPhotoPlaceholder?
    let isLikeEnabled: Bool
    let zoom: FeedPhotoZoom
    let onTransitionReady: () -> Void
    let onPhotoFrameChanged: (CGRect) -> Void
    let onPhotoInteractionChanged: (Bool) -> Void
    let onLike: () -> Void
    @State private var imageRatio: CGFloat?
    @State private var resolvedImageRatio: CGFloat?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var imageFrame: CGRect?
    @State private var isOriginalLoaded = false
    @State private var gestureScale: CGFloat = 1
    @State private var gestureOffset: CGSize = .zero
    @State private var isGestureActive = false
    @State private var photoSize: CGSize = .zero
    @State private var touchPositions: [ObjectIdentifier: CGPoint] = [:]

    init(
        post: FeedPost?,
        placeholder: FeedPhotoPlaceholder? = nil,
        isLikeEnabled: Bool = true,
        zoom: FeedPhotoZoom = FeedPhotoZoom(),
        onTransitionReady: @escaping () -> Void = {},
        onPhotoFrameChanged: @escaping (CGRect) -> Void = { _ in },
        onPhotoInteractionChanged: @escaping (Bool) -> Void = { _ in },
        onLike: @escaping () -> Void
    ) {
        self.post = post
        self.placeholder = placeholder
        self.isLikeEnabled = isLikeEnabled
        self.zoom = zoom
        self.onTransitionReady = onTransitionReady
        self.onPhotoFrameChanged = onPhotoFrameChanged
        self.onPhotoInteractionChanged = onPhotoInteractionChanged
        self.onLike = onLike
        _imageRatio = State(initialValue: placeholder?.heightToWidthRatio)
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Color.clear
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1 / (imageRatio ?? Metrics.defaultImageRatio), contentMode: .fit)
                    .overlay {
                        photoImage
                            .feedZoomEffect(
                                progress: zoom.photoProgress,
                                source: zoom.source,
                                destinationFrame: imageFrame,
                                morphsAspectRatio: zoom.morphsAspectRatio
                            )
                    }
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
                            .opacity(signatureOpacity)
                            .padding(theme.spacing.sm)
                        }
                    }
                    .onGeometryChange(for: CGSize.self) { $0.size } action: { photoSize = $0 }
                    .scaleEffect(gestureScale)
                    .offset(gestureOffset)
                    .feedZoomViewportClip(
                        progress: zoom.photoProgress,
                        source: isPhotoInteracting ? nil : zoom.source,
                        destination: zoom.viewport,
                        canvas: imageFrame,
                        contentScale: gestureScale,
                        contentOffset: gestureOffset
                    )
                    .opacity(photoOpacity)
                    .onGeometryChange(for: CGRect.self) { proxy in
                        proxy.frame(in: .global)
                    } action: { frame in
                        // 핀치 중 전환용 프레임을 다시 적용하면 확대 크기가 다음 프레임에 되먹임된다.
                        if !isGestureActive, zoom.isSettled || imageFrame == nil {
                            imageFrame = frame
                            onPhotoFrameChanged(frame)
                        }
                    }
            }
            // clip 분기가 바뀌어도 ZStack 바깥의 터치 뷰는 유지한다.
            .overlay {
                FeedPhotoTouchSurface(isEnabled: zoom.isSettled, onTouchesChanged: updateTouches)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .zIndex(1)
            .task(id: imageFrame != nil) {
                guard imageFrame != nil else { return }
                onTransitionReady()
            }
            .task(id: post?.originalImageSource) {
                if imageRatio == nil, let placeholder {
                    if let ratio = await ImageRatioLoader.ratio(for: placeholder.imageSource) {
                        resolveImageRatio(ratio)
                    }
                }
                guard let post,
                      let ratio = await ImageRatioLoader.ratio(for: post.originalImageSource)
                else { return }
                resolveImageRatio(ratio)
            }
            .onChange(of: zoom.isSettled) { _, isSettled in
                if isSettled, let resolvedImageRatio { applyImageRatio(resolvedImageRatio) }
            }
            .onChange(of: isPhotoInteracting) { _, isInteracting in
                onPhotoInteractionChanged(isInteracting)
            }

            if let post {
                FeedLikeRow(
                    title: post.title,
                    likeCount: post.likeCount,
                    isLiked: post.isLiked,
                    isEnabled: isLikeEnabled,
                    onLike: onLike
                )
                .opacity(zoom.contentProgress)
            }
        }
    }

    private func updateTouches(_ touches: [FeedPhotoTouch], origin: CGPoint) {
        if touches.isEmpty {
            resetGesture()
            return
        }
        guard zoom.isSettled else { return }
        let positions = Dictionary(uniqueKeysWithValues: touches.map {
            ($0.id, CGPoint(x: $0.location.x - origin.x, y: $0.location.y - origin.y))
        })

        if touches.count >= 2 {
            isGestureActive = true
            let first = touches[0]
            let second = touches[1]
            if touchPositions.count >= 2,
               let previousFirst = touchPositions[first.id],
               let previousSecond = touchPositions[second.id],
               let currentFirst = positions[first.id],
               let currentSecond = positions[second.id] {
                let previousDistance = hypot(previousFirst.x - previousSecond.x, previousFirst.y - previousSecond.y)
                let currentDistance = hypot(currentFirst.x - currentSecond.x, currentFirst.y - currentSecond.y)
                let previousScale = gestureScale
                if previousDistance > 0 {
                    gestureScale = min(max(gestureScale * currentDistance / previousDistance, 1), Metrics.maximumPinchScale)
                }
                let previousCenter = CGPoint(
                    x: (previousFirst.x + previousSecond.x) / 2,
                    y: (previousFirst.y + previousSecond.y) / 2
                )
                let currentCenter = CGPoint(
                    x: (currentFirst.x + currentSecond.x) / 2,
                    y: (currentFirst.y + currentSecond.y) / 2
                )
                gestureOffset = limitedOffset(
                    feedPhotoPinchOffset(
                        offset: gestureOffset,
                        previousScale: previousScale,
                        nextScale: gestureScale,
                        previousCenter: previousCenter,
                        currentCenter: currentCenter,
                        photoSize: photoSize
                    ),
                    scale: gestureScale
                )
            }
        } else if let touch = touches.first,
                  isPhotoInteracting,
                  let previous = touchPositions[touch.id],
                  let current = positions[touch.id] {
            gestureOffset = limitedOffset(
                CGSize(
                    width: gestureOffset.width + current.x - previous.x,
                    height: gestureOffset.height + current.y - previous.y
                ),
                scale: gestureScale
            )
        }

        touchPositions = positions
    }

    private func resetGesture() {
        isGestureActive = false
        gestureScale = 1
        gestureOffset = .zero
        touchPositions = [:]
    }

    private func limitedOffset(_ offset: CGSize, scale: CGFloat) -> CGSize {
        let limitX = photoSize.width * scale / 2
        let limitY = photoSize.height * scale / 2
        return CGSize(
            width: min(max(offset.width, -limitX), limitX),
            height: min(max(offset.height, -limitY), limitY)
        )
    }

    private var isPhotoInteracting: Bool {
        isGestureActive || gestureScale > 1 || gestureOffset != .zero
    }

    private var photoImage: some View {
        ZStack {
            if let placeholder {
                FeedPlaceholderImage(
                    preloaded: placeholder.preloadedImage,
                    source: placeholder.imageSource,
                    contentMode: .fill
                )
            }
            if let post {
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
                .animation(zoom.isSettled ? Metrics.originalRevealAnimation : nil, value: isOriginalRevealed)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
    }

    private func resolveImageRatio(_ ratio: CGFloat) {
        resolvedImageRatio = ratio
        // 이동 중 도착 프레임이 바뀌면 사진 위치도 튀므로 비율 변경을 미룬다.
        if !zoom.morphsAspectRatio || zoom.isSettled { applyImageRatio(ratio) }
    }

    private func applyImageRatio(_ ratio: CGFloat) {
        guard imageRatio != ratio else { return }
        if zoom.morphsAspectRatio, !reduceMotion {
            withAnimation(Metrics.ratioAnimation) { imageRatio = ratio }
        } else {
            imageRatio = ratio
        }
    }

    // Android SharedFeedImage와 같이, 출발 이미지가 있으면 원본은 로드됐고 열림 전환이 끝난 뒤에만 드러낸다.
    private var isOriginalRevealed: Bool {
        placeholder == nil || (isOriginalLoaded && (zoom.isSettled || zoom.keepsOriginalVisible))
    }

    private var photoOpacity: CGFloat {
        zoom.source == nil ? zoom.contentProgress : 1
    }

    private var signatureZoomSource: FeedZoomRegistry.Source? {
        zoom.source?.signatureFrame.map { FeedZoomRegistry.Source(frame: $0, cornerRadius: 0) }
    }

    // 변환된 서명의 global 좌표를 다시 목적지로 쓰면 느린 스와이프 중 위치가 되먹임된다.
    // 목적지는 움직이지 않는 사진 영역과 동일한 우하단 배치 규칙에서 구한다.
    private var signatureFrame: CGRect? {
        imageFrame.map { frame in
            CGRect(
                x: frame.maxX - theme.spacing.sm - Metrics.signatureSize.width,
                y: frame.maxY - theme.spacing.sm - Metrics.signatureSize.height,
                width: Metrics.signatureSize.width,
                height: Metrics.signatureSize.height
            )
        }
    }

    // 사진은 날아가는데 출발 서명 위치를 모르면, 서명은 제자리에서 다른 콘텐츠와 함께 페이드된다.
    private var signatureOpacity: CGFloat {
        zoom.source != nil && signatureZoomSource == nil ? zoom.contentProgress : 1
    }
}

func feedPhotoPinchOffset(
    offset: CGSize,
    previousScale: CGFloat,
    nextScale: CGFloat,
    previousCenter: CGPoint,
    currentCenter: CGPoint,
    photoSize: CGSize
) -> CGSize {
    let center = CGPoint(x: photoSize.width / 2, y: photoSize.height / 2)
    let scaleChange = nextScale / previousScale
    return CGSize(
        width: currentCenter.x - center.x - (previousCenter.x - center.x - offset.width) * scaleChange,
        height: currentCenter.y - center.y - (previousCenter.y - center.y - offset.height) * scaleChange
    )
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

struct FeedLikeRow: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String?
    let likeCount: Int
    let isLiked: Bool
    let isEnabled: Bool
    let onLike: () -> Void

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: Metrics.rowHeight)
            .overlay {
                HStack(spacing: theme.spacing.md) {
                    Button(action: onLike) {
                        HStack(spacing: Metrics.likeSpacing) {
                            Image(isLiked ? "ic_heart_filled" : "ic_heart")
                                .renderingMode(.template)
                                .resizable()
                                .frame(width: Metrics.heartSize, height: Metrics.heartSize)
                                .foregroundStyle(
                                    isLiked ? theme.colors.actionPrimary : theme.colors.textSecondary
                                )
                                .accessibilityHidden(true)

                            Text("\(likeCount)")
                                .font(theme.typography.body)
                                .fontWeight(.regular)
                                .foregroundStyle(theme.colors.textSecondary)
                        }
                        .frame(minHeight: Metrics.minimumTouchSize)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!isEnabled)
                    .accessibilityLabel("좋아요 \(likeCount)")
                    .accessibilityValue(isLiked ? "선택됨" : "")

                    Text(displayTitle)
                        .font(theme.typography.body)
                        .fontWeight(.regular)
                        .foregroundStyle(theme.colors.textSecondary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(.horizontal, Metrics.horizontalInset)
            }
            .overlay(alignment: .bottom) {
                Rectangle().fill(theme.colors.border).frame(height: 0.5).accessibilityHidden(true)
            }
    }

    private var displayTitle: String {
        guard let trimmed = title?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return "무제" }
        return trimmed
    }
}

private enum Metrics {
    static let defaultImageRatio: CGFloat = 1
    static let maximumPinchScale: CGFloat = 4
    static let ratioAnimation: Animation = .smooth(duration: 0.3)
    // Android PHOTO_CROSSFADE_DURATION_MILLIS(180) + tween 기본 FastOutSlowInEasing.
    static let originalRevealAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.18)
    static let signatureSize = CGSize(width: 70, height: 52)
    static let rowHeight: CGFloat = 57
    static let likeSpacing: CGFloat = 9
    static let heartSize: CGFloat = 24
    static let minimumTouchSize: CGFloat = 44
    static let horizontalInset: CGFloat = 14
}

#Preview("Feed Photo", traits: .sizeThatFitsLayout) {
    FeedPhoto(
        post: FeedPreviewData.content.post,
        onLike: {}
    )
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
