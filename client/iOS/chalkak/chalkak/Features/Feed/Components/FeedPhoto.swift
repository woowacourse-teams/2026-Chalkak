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
    // nil이면 사인을 누를 수 없다(작성자 ID를 모르거나 사인 탭을 쓰지 않는 화면).
    let onSignatureTap: (() -> Void)?
    @State private var imageRatio: CGFloat?
    @State private var imageSwap: FeedPhotoImageSwap
    @State private var didReportReady = false
    @State private var hasResolvedTransitionImage = false
    @State private var transitionImageFailed = false
    @State private var imageFrame: CGRect?
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
        onSignatureTap: (() -> Void)? = nil,
        onLike: @escaping () -> Void
    ) {
        self.post = post
        self.placeholder = placeholder
        self.isLikeEnabled = isLikeEnabled
        self.zoom = zoom
        self.onTransitionReady = onTransitionReady
        self.onPhotoFrameChanged = onPhotoFrameChanged
        self.onPhotoInteractionChanged = onPhotoInteractionChanged
        self.onSignatureTap = onSignatureTap
        self.onLike = onLike
        let cached = placeholder?.preloadedImage ?? placeholder?.imageSource.cachedImage()
            ?? post?.originalImageSource.cachedImage()
        _imageSwap = State(initialValue: FeedPhotoImageSwap(displayedImage: cached))
        _imageRatio = State(initialValue: cached.map { $0.size.height / $0.size.width } ?? placeholder?.heightToWidthRatio ?? Metrics.defaultImageRatio)
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
                        if let signature = displayedSignature {
                            FeedPlaceholderImage(
                                preloaded: signature.preloaded,
                                source: signature.source,
                                contentMode: .fit
                            )
                            .frame(width: signatureSize.width, height: signatureSize.height)
                            .feedZoomEffect(
                                progress: zoom.photoProgress,
                                source: signatureZoomSource,
                                destinationFrame: signatureFrame
                            )
                            .modifier(FeedScreenSignatureEffect(screen: zoom.screen, destinationFrame: signatureFrame))
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
                        if !isGestureActive, zoom.isSettled || !didReportReady {
                            imageFrame = frame
                            onPhotoFrameChanged(frame)
                            reportReadyIfMeasured()
                        }
                    }
            }
            // clip 분기가 바뀌어도 ZStack 바깥의 터치 뷰는 유지한다.
            .overlay {
                FeedPhotoTouchSurface(isEnabled: zoom.isSettled, onTouchesChanged: updateTouches)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            // 터치 표면보다 위에 두어야 사인 탭이 사진 확대 제스처에 가려지지 않는다.
            .overlay(alignment: .bottomTrailing) {
                if let onSignatureTap, zoom.isSettled, !isPhotoInteracting {
                    signatureButton(action: onSignatureTap)
                }
            }
            .zIndex(1)
            .task(id: placeholder?.imageSource ?? post?.originalImageSource) {
                // 위치 측정만 끝난 상태에서 불투명한 상세 화면을 열면 목록 사진을
                // 빈 배경으로 가린다. 전환에 쓸 비트맵도 확보한 뒤 준비 완료를 알린다.
                guard let source = placeholder?.imageSource ?? post?.originalImageSource else { return }
                if imageSwap.displayedImage == nil {
                    var image = source.cachedImage()
                    if image == nil, case let .remote(url?) = source {
                        image = try? await RemoteImageCache.shared.load(url)
                    }
                    guard !Task.isCancelled else { return }
                    if imageSwap.displayedImage == nil {
                        imageSwap.displayedImage = image
                        if case .system = source {
                            transitionImageFailed = false
                        } else {
                            transitionImageFailed = image == nil
                        }
                    }
                }
                hasResolvedTransitionImage = true
                reportReadyIfMeasured()
            }
            .task(id: post?.originalImageSource) {
                guard let post else { return }
                // 원본 서명도 함께 받아 두고 사진과 같은 프레임에 바꾼다. 따로 받으면 받는 동안 서명이 비었다가 나타난다.
                async let signature = Self.loadImage(post.signatureImageSource)
                let image = await Self.loadImage(post.originalImageSource)
                let signatureImage = await signature
                guard !Task.isCancelled else { return }
                if let image {
                    var transaction = Transaction()
                    transaction.disablesAnimations = true
                    withTransaction(transaction) {
                        imageSwap.receiveOriginal(image, signature: signatureImage, isSettled: zoom.isSettled)
                    }
                }
                // 목록에서 받은 썸네일도 원본 비율이다. 원본은 화질만 교체하고
                // 이미 펼친 사진의 크기는 다시 바꾸지 않는다.
                if placeholder == nil, let image {
                    imageRatio = image.size.height / image.size.width
                }
                if image != nil {
                    // 원본이 먼저 준비되면 느린 썸네일 요청을 기다릴 필요가 없다.
                    hasResolvedTransitionImage = true
                    reportReadyIfMeasured()
                }
            }
            .onChange(of: zoom.isSettled) { _, isSettled in
                guard isSettled else { return }
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { imageSwap.finishTransition() }
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

    // 사진 위에 그려진 사인 자리를 덮는 투명 버튼. 사인 둘레 여백까지 눌리게 해 44pt 이상을 확보한다.
    private func signatureButton(action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Color.clear
                .frame(width: signatureSize.width, height: signatureSize.height)
                .padding(theme.spacing.sm)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(post?.isOwnedByCurrentUser == true ? "내 사인" : "작성자 사인")
        .accessibilityHint("사진 모아보기")
        .accessibilityIdentifier("feed-signature")
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
        // 전환 전체에서 하나의 이미지 뷰를 유지하고 비트맵만 교체한다.
        // 원본 로더를 별도 레이어로 겹치면 로드 콜백과 표시 시점이 달라질 수 있다.
        FeedPlaceholderImage(
            preloaded: imageSwap.displayedImage ?? placeholder?.preloadedImage,
            source: transitionImageFailed ? .system("photo.badge.exclamationmark")
                : placeholder?.imageSource ?? post?.originalImageSource ?? .remote(nil),
            contentMode: .fill
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .accessibilityLabel(post?.contentDescription ?? "")
    }

    private func reportReadyIfMeasured() {
        guard !didReportReady, hasResolvedTransitionImage, let imageFrame, let imageRatio,
              imageFrame.width > 0,
              abs(imageFrame.height - imageFrame.width * imageRatio) < 1 else { return }
        didReportReady = true
        onTransitionReady()
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
                x: frame.maxX - theme.spacing.sm - signatureSize.width,
                y: frame.maxY - theme.spacing.sm - signatureSize.height,
                width: signatureSize.width,
                height: signatureSize.height
            )
        }
    }

    // 출발 뷰에서 사진 대비 서명 비율을 그대로 이어받아, 확대·축소 중에도 서명이 사진과 같은 비율로 보이게 한다.
    // 출발 서명을 모르면 기본 크기를 쓴다.
    private var signatureSize: CGSize {
        guard let source = zoom.source,
              let sourceSignature = source.signatureFrame,
              let imageFrame,
              source.frame.width > 0 else { return Metrics.defaultSignatureSize }
        let scale = imageFrame.width / source.frame.width
        return CGSize(width: sourceSignature.width * scale, height: sourceSignature.height * scale)
    }

    // 사진은 날아가는데 출발 서명 위치를 모르면, 서명은 제자리에서 다른 콘텐츠와 함께 페이드된다.
    // 원본 서명을 받아 둔 뒤에만 원본으로 바꾸고, 그 전에는 출발 화면의 서명을 계속 보여준다.
    private var displayedSignature: (source: ChalkakImageSource, preloaded: UIImage?)? {
        if imageSwap.hasOriginal, let post, let image = imageSwap.originalSignature {
            return (post.signatureImageSource, image)
        }
        if let source = placeholder?.signatureImageSource {
            return (source, placeholder?.preloadedSignatureImage)
        }
        return imageSwap.hasOriginal ? post.map { ($0.signatureImageSource, nil) } : nil
    }

    private static func loadImage(_ source: ChalkakImageSource) async -> UIImage? {
        if let cached = source.cachedImage() { return cached }
        guard case let .remote(url?) = source else { return nil }
        return try? await RemoteImageCache.shared.load(url)
    }

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
/// 이동 중에는 출발 화면의 비트맵을 유지하고, 도착 후 원본으로 한 번 교체한다.
struct FeedPhotoImageSwap {
    var displayedImage: UIImage?
    private(set) var hasOriginal = false
    private(set) var originalSignature: UIImage?
    private var pendingOriginal: UIImage?
    private var pendingSignature: UIImage?

    init(displayedImage: UIImage?) {
        self.displayedImage = displayedImage
    }

    mutating func receiveOriginal(_ image: UIImage, signature: UIImage? = nil, isSettled: Bool) {
        pendingOriginal = image
        pendingSignature = signature
        if isSettled || displayedImage == nil { finishTransition() }
    }

    mutating func finishTransition() {
        guard let pendingOriginal else { return }
        displayedImage = pendingOriginal
        originalSignature = pendingSignature
        hasOriginal = true
        self.pendingOriginal = nil
        pendingSignature = nil
    }
}

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
    static let defaultSignatureSize = CGSize(width: 70, height: 52)
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
