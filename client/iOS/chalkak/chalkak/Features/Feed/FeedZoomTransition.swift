import SwiftUI

/// Feed로 줌 전환할 때 출발 뷰를 구분하는 식별자.
/// 같은 게시물이 여러 카드와 랭킹에 함께 보일 수 있어 위치별로 나눈다.
enum FeedZoomSource: Hashable, Sendable {
    case displayFeatured(FeedPost.ID)
    case displayGrid(FeedPost.ID)
    case homeYesterday(FeedPost.ID)
    case homeTrending(FeedPost.ID)
    case record(FeedPost.ID)

    var viewport: FeedZoomViewport? {
        switch self {
        case .displayFeatured, .displayGrid: .display
        case .homeYesterday, .homeTrending: .home
        case .record: nil
        }
    }
}

enum FeedZoomViewport: Hashable {
    case home, display
}

/// 줌 전환 출발 뷰들의 현재 화면상 위치를 모아 둔다.
/// Feed는 열리고 닫힐 때 이 위치와 자신의 사진 위치 사이를 보간해 사진만 확대·축소한다.
@MainActor
@Observable
final class FeedZoomRegistry {
    // Avoid the isolated-deinit back-deployment crash on iOS 26.2 and older.
    // No actor-bound cleanup is needed. See swiftlang/swift#88036.
    nonisolated deinit {}

    struct Source: Sendable {
        let frame: CGRect
        let cornerRadius: CGFloat
        // Fit 사진보다 큰 카드의 마스크를 보존한다. 기본 출발 뷰는 사진 자체를 클립한다.
        var clipFrame: CGRect?
        // 출발 뷰의 서명 위치. Android처럼 서명은 사진과 따로 이 위치에서 출발한다.
        var signatureFrame: CGRect?
        var visibleBounds: CGRect?
        var registrationID: UUID?
        // 홈 랭킹처럼 패딩 안쪽 가로 스크롤에 걸친 카드는 그 경계에서 잘려 보인다.
        var containerBounds: CGRect?

        func acceptsTap(at location: CGPoint) -> Bool {
            (clipFrame ?? frame).contains(location)
                && (visibleBounds?.contains(location) ?? true)
        }
    }

    // 사진이 이동하는 동안 숨길 출발 뷰. 이동 중인 사진과 겹쳐 두 장으로 보이지 않게 한다.
    var activeSource: FeedZoomSource?
    // 뒤로가기 스와이프로 창이 떠 있는 동안에는 가려진 부분까지 숨겨 같은 사진이 남지 않게 한다.
    var hidesWholeSource = false
    var sourceBarOpacity: CGFloat = 0
    @ObservationIgnored private var sources: [FeedZoomSource: Source] = [:]
    @ObservationIgnored private var viewports: [FeedZoomViewport: CGRect] = [:]
    @ObservationIgnored var bottomBarFrame: CGRect?

    func source(for id: FeedZoomSource) -> Source? {
        guard var source = sources[id] else { return nil }
        var visible = source.containerBounds
        if let viewport = id.viewport, let bounds = viewports[viewport] {
            let bottom = bottomBarFrame.map { min(bounds.maxY, $0.minY) } ?? bounds.maxY
            let viewportBounds = CGRect(x: bounds.minX, y: bounds.minY, width: bounds.width, height: max(0, bottom - bounds.minY))
            let clipped = visible?.intersection(viewportBounds) ?? .null
            visible = clipped.isNull ? viewportBounds : clipped
        }
        source.visibleBounds = visible
        return source
    }

    func updateViewport(_ frame: CGRect, for viewport: FeedZoomViewport) {
        viewports[viewport] = frame
    }

    @discardableResult
    func update(_ source: Source, for id: FeedZoomSource) -> Source {
        sources[id] = source
        return self.source(for: id) ?? source
    }

    func remove(_ id: FeedZoomSource, registrationID: UUID? = nil) {
        // 그리드 재배치로 같은 사진의 새 셀이 먼저 등록돼도 이전 셀이 지우지 않게 한다.
        guard sources[id]?.registrationID == registrationID else { return }
        sources[id] = nil
    }
}

/// Feed 사진의 줌 전환 상태.
struct FeedPhotoZoom {
    // 사진 이동 진행도(0: 출발 뷰 위치, 1: 제자리).
    var photoProgress: CGFloat = 1
    // 사진 외 콘텐츠의 페이드 진행도(0: 투명, 1: 불투명).
    var contentProgress: CGFloat = 1
    // 사진이 출발할 위치. 없으면 사진도 다른 콘텐츠와 함께 페이드된다.
    var source: FeedZoomRegistry.Source?
    // 열림 전환이 끝나 멈춘 상태. 이때만 원본 이미지를 드러낸다.
    var isSettled = true
    var morphsAspectRatio = false
    var viewport: CGRect?
    var keepsOriginalVisible = false
    // 화면 전체가 출발 카드로 접히는 전환. 이때 서명은 화면과 함께 줄지 않고 출발 서명에 맞춘다.
    var screen: FeedScreenZoom?
}

/// `FeedScreenReturnEffect`에 넘기는 화면 전환 상태.
struct FeedScreenZoom {
    var progress: CGFloat
    var dragOffset: CGFloat
    let source: FeedZoomRegistry.Source
    let photo: CGRect
    let canvas: CGRect
    let safeAreaInsets: EdgeInsets
    let revealsCoveredSource: Bool
}

extension EnvironmentValues {
    /// 없으면 줌 전환 없이 Feed가 페이드로만 열린다.
    @Entry var feedZoomRegistry: FeedZoomRegistry?
    @Entry var feedZoomContainerBounds: CGRect?
}

extension View {
    /// 이 뷰를 Feed 줌 전환의 출발 뷰로 등록한다. 사진이 이동하는 동안 이 뷰만 숨긴다.
    /// 서명이 `ChalkakSignedImage` 규칙(우하단, sm 여백)으로 올라가 있으면 그 크기를 함께 넘긴다.
    func feedZoomSource(
        _ source: FeedZoomSource,
        cornerRadius: CGFloat = 0,
        signatureSize: CGSize? = nil,
        fittedImageAspectRatio: CGFloat? = nil,
        clipsToContainer: Bool = false,
        onSelect: ((FeedZoomRegistry.Source) -> Void)? = nil
    ) -> some View {
        modifier(
            FeedZoomSourceModifier(
                source: source, cornerRadius: cornerRadius, signatureSize: signatureSize,
                fittedImageAspectRatio: fittedImageAspectRatio, clipsToContainer: clipsToContainer, onSelect: onSelect
            )
        )
    }

    func feedZoomViewport(_ viewport: FeedZoomViewport) -> some View {
        modifier(FeedZoomViewportModifier(viewport: viewport))
    }

    /// 안쪽 출발 뷰를 잘라 보이는 컨테이너(가로 스크롤 등)로 등록한다.
    /// 이동하는 사진도 출발·복귀 때 이 경계 밖으로 그리지 않는다.
    func feedZoomContainer() -> some View {
        modifier(FeedZoomContainerModifier())
    }

    func feedZoomViewportClip(
        progress: CGFloat,
        source: FeedZoomRegistry.Source?,
        destination: CGRect?,
        canvas: CGRect?,
        contentScale: CGFloat = 1,
        contentOffset: CGSize = .zero
    ) -> some View {
        clipShape(FeedZoomViewportClip(
            progress: progress,
            source: source?.visibleBounds,
            destination: destination,
            canvas: canvas,
            sourcePhotoFrame: source?.frame,
            contentScale: contentScale,
            contentOffset: contentOffset
        ))
    }

    /// 전환 진행도(0: 출발 뷰 위치, 1: 제자리)에 맞춰 이 뷰를 출발 뷰 위치에서 제자리로 옮긴다.
    func feedZoomEffect(
        progress: CGFloat,
        source: FeedZoomRegistry.Source?,
        destinationFrame: CGRect?,
        morphsAspectRatio: Bool = false
    ) -> some View {
        modifier(
            FeedZoomEffect(progress: progress, source: source, destinationFrame: destinationFrame, morphsAspectRatio: morphsAspectRatio)
        )
    }
}

private struct FeedZoomSourceModifier: ViewModifier {
    @Environment(\.feedZoomRegistry) private var registry
    @Environment(\.feedZoomContainerBounds) private var containerBounds
    @Environment(\.chalkakTheme) private var theme
    let source: FeedZoomSource
    let cornerRadius: CGFloat
    let signatureSize: CGSize?
    let fittedImageAspectRatio: CGFloat?
    let clipsToContainer: Bool
    let onSelect: ((FeedZoomRegistry.Source) -> Void)?
    @State private var frame: CGRect?
    @State private var registrationID = UUID()

    @ViewBuilder
    func body(content: Content) -> some View {
        if let onSelect {
            registeredContent(content)
                .overlay {
                    GeometryReader { proxy in
                        Color.clear
                            .contentShape(Rectangle())
                            .onTapGesture(coordinateSpace: .global) { location in
                                // Lazy 셀의 이전 등록 여부와 무관하게 실제 탭한 위치를 전달한다.
                                let measured = register(proxy.frame(in: .global))
                                // 하단바나 스크롤 경계 뒤에 가려진 부분에서는 전환을 시작하지 않는다.
                                guard measured.acceptsTap(at: location) else { return }
                                onSelect(measured)
                            }
                    }
                    .accessibilityHidden(true)
                }
                .accessibilityAction {
                    guard let frame else { return }
                    onSelect(register(frame))
                }
        } else {
            registeredContent(content)
        }
    }

    private func registeredContent(_ content: Content) -> some View {
        content
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .global)
            } action: { newFrame in
                frame = newFrame
                register(newFrame)
            }
            // 기록처럼 같은 뷰에서 게시물만 바뀌면 위치가 그대로라 위치 변경으로는 다시 등록되지 않는다.
            .onChange(of: source) { oldSource, _ in
                registry?.remove(oldSource, registrationID: registrationID)
                if let frame {
                    register(frame)
                }
            }
            .onChange(of: fittedImageAspectRatio) { _, _ in
                if let frame { register(frame) }
            }
            .onChange(of: containerBounds) { _, _ in
                if let frame { register(frame) }
            }
            .mask {
                if registry?.activeSource == source {
                    FeedZoomSourceRemainder(hidden: hiddenBounds)
                        .fill(style: FillStyle(eoFill: true))
                } else {
                    Rectangle()
                }
            }
            .onAppear { if let frame { register(frame) } }
            .onDisappear { registry?.remove(source, registrationID: registrationID) }
    }

    // 이동하는 사진이 대신 그리는 영역. 하단바·패딩에 가려진 부분은 이동하는 사진이 그리지 않으므로 남긴다.
    // 전부 숨기면 반투명 하단바 아래가 비고, 전부 남기면 복귀 스와이프 중 같은 사진이 두 장 보인다.
    private var hiddenBounds: CGRect? {
        guard registry?.hidesWholeSource != true else { return nil }
        guard let frame, let visible = registry?.source(for: source)?.visibleBounds else { return nil }
        return visible.offsetBy(dx: -frame.minX, dy: -frame.minY)
    }

    @discardableResult
    private func register(_ frame: CGRect) -> FeedZoomRegistry.Source {
        let measured = FeedZoomRegistry.Source(
            frame: FeedZoomCropGeometry.fittedFrame(in: frame, heightToWidthRatio: fittedImageAspectRatio),
            cornerRadius: cornerRadius,
            clipFrame: clipsToContainer ? frame : nil,
            signatureFrame: signatureSize.map { size in
                CGRect(
                    x: frame.maxX - theme.spacing.sm - size.width,
                    y: frame.maxY - theme.spacing.sm - size.height,
                    width: size.width,
                    height: size.height
                )
            },
            registrationID: registrationID,
            containerBounds: containerBounds
        )
        return registry?.update(measured, for: source) ?? measured
    }
}

private struct FeedZoomContainerModifier: ViewModifier {
    @State private var bounds: CGRect?

    func body(content: Content) -> some View {
        content
            .environment(\.feedZoomContainerBounds, bounds)
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { bounds = $0 }
    }
}

private struct FeedZoomViewportModifier: ViewModifier {
    @Environment(\.feedZoomRegistry) private var registry
    let viewport: FeedZoomViewport

    func body(content: Content) -> some View {
        content.onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: {
            registry?.updateViewport($0, for: viewport)
        }
    }
}

/// 출발 뷰에서 `hidden` 영역만 뺀 모양. `hidden`이 없으면 전부 숨긴다.
struct FeedZoomSourceRemainder: Shape {
    let hidden: CGRect?

    func path(in rect: CGRect) -> Path {
        guard let hidden else { return Path() }
        var path = Path(rect)
        let overlap = rect.intersection(hidden)
        if !overlap.isNull { path.addRect(overlap) }
        return path
    }
}

struct FeedZoomViewportClip: Shape {
    var progress: CGFloat
    let source: CGRect?
    let destination: CGRect?
    let canvas: CGRect?
    var sourcePhotoFrame: CGRect?
    var contentScale: CGFloat = 1
    var contentOffset: CGSize = .zero

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func path(in rect: CGRect) -> Path {
        guard let source, let destination, let canvas else {
            // 측정 전이나 핀치 중에도 같은 뷰 구조를 유지한다. 확대·이동한 사진 전체를 포함한다.
            let contentBounds = CGRect(
                x: rect.midX - rect.width * contentScale / 2 + contentOffset.width,
                y: rect.midY - rect.height * contentScale / 2 + contentOffset.height,
                width: rect.width * contentScale,
                height: rect.height * contentScale
            )
            var bounds = rect.union(contentBounds)
            // 기록은 출발 viewport가 없다. 사진이 이동하는 경로까지 클립 안에 둔다.
            if let sourcePhotoFrame, let canvas {
                bounds = bounds.union(sourcePhotoFrame.offsetBy(dx: -canvas.minX, dy: -canvas.minY))
            }
            return Path(bounds)
        }
        let progress = min(max(progress, 0), 1)
        let bounds = CGRect(
            x: source.minX + (destination.minX - source.minX) * progress,
            y: source.minY + (destination.minY - source.minY) * progress,
            width: source.width + (destination.width - source.width) * progress,
            height: source.height + (destination.height - source.height) * progress
        )
        return Path(bounds.offsetBy(dx: -canvas.minX, dy: -canvas.minY))
    }
}

private struct FeedZoomEffect: ViewModifier, Animatable {
    var progress: CGFloat
    let source: FeedZoomRegistry.Source?
    let destinationFrame: CGRect?
    let morphsAspectRatio: Bool

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let geometry = cropGeometry
        let remaining = 1 - progress
        let scale = geometry?.scale ?? (startScale + (1 - startScale) * progress)
        let offset = geometry?.offset ?? CGSize(width: startOffset.width * remaining, height: startOffset.height * remaining)
        let contentSize = geometry == nil ? nil : destinationFrame?.size
        let fillScale: CGFloat
        if let geometry, let contentSize {
            fillScale = max(1, geometry.size.height / contentSize.height)
        } else {
            fillScale = 1
        }

        // 사진 레이아웃은 고정한다. 매 프레임 높이를 바꾸면 이미지가 재배치되며
        // 소수점 크기가 픽셀 경계에 반올림되어 느린 드래그 중 사진 내부가 떨린다.
        return content
            .frame(width: contentSize?.width, height: contentSize?.height)
            .scaleEffect(fillScale)
            .clipShape(FeedZoomCropBounds(size: geometry?.size))
            .clipShape(FeedZoomPhotoClip(
                source: source,
                remaining: remaining,
                scale: scale,
                cornerRadius: geometry?.cornerRadius ?? (source?.cornerRadius ?? 0) * remaining / scale,
                cropSize: geometry?.size
            ))
            .scaleEffect(scale)
            .offset(offset)
            .opacity(source != nil && destinationFrame == nil ? 0 : 1)
    }

    private var cropGeometry: FeedZoomCropGeometry? {
        guard morphsAspectRatio, let source, let destinationFrame,
              destinationFrame.width > 0, destinationFrame.height > 0, source.frame.width > 0
        else { return nil }
        return FeedZoomCropGeometry(progress: progress, source: source, destination: destinationFrame)
    }

    // 출발 뷰 안에 사진이 비율을 유지한 채 들어가는 배율.
    private var startScale: CGFloat {
        guard let source, let destinationFrame,
              destinationFrame.width > 0, destinationFrame.height > 0
        else { return 1 }
        return min(
            source.frame.width / destinationFrame.width,
            source.frame.height / destinationFrame.height
        )
    }

    private var startOffset: CGSize {
        guard let source, let destinationFrame else { return .zero }
        return CGSize(
            width: source.frame.midX - destinationFrame.midX,
            height: source.frame.midY - destinationFrame.midY
        )
    }
}

private struct FeedZoomCropBounds: Shape {
    let size: CGSize?

    func path(in rect: CGRect) -> Path {
        let size = size ?? rect.size
        return Path(CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2, width: size.width, height: size.height))
    }
}

/// 크롭 영역을 바꾸며 사진 자체의 종횡비는 유지한다.
struct FeedZoomCropGeometry {
    let size: CGSize
    let scale: CGFloat
    let offset: CGSize
    let cornerRadius: CGFloat

    static func fittedFrame(in frame: CGRect, heightToWidthRatio: CGFloat?) -> CGRect {
        guard let ratio = heightToWidthRatio, ratio > 0, frame.width > 0, frame.height > 0 else { return frame }
        let width = min(frame.width, frame.height / ratio)
        let height = width * ratio
        return CGRect(x: frame.midX - width / 2, y: frame.midY - height / 2, width: width, height: height)
    }

    init(progress: CGFloat, source: FeedZoomRegistry.Source, destination: CGRect) {
        let progress = min(max(progress, 0), 1)
        let remaining = 1 - progress
        let startScale = source.frame.width / destination.width
        scale = startScale + (1 - startScale) * progress
        size = CGSize(
            width: destination.width,
            height: (source.frame.height + (destination.height - source.frame.height) * progress) / scale
        )
        offset = CGSize(
            width: (source.frame.midX - destination.midX) * remaining,
            height: (source.frame.midY - destination.midY) * remaining
        )
        cornerRadius = source.cornerRadius * remaining / scale
    }
}

/// 카드의 둥근 마스크를 실제 fit 사진 좌표로 옮겨 여백을 사진 모서리로 오인하지 않는다.
struct FeedZoomPhotoClip: Shape {
    let source: FeedZoomRegistry.Source?
    let remaining: CGFloat
    let scale: CGFloat
    let cornerRadius: CGFloat
    var cropSize: CGSize? = nil

    func path(in rect: CGRect) -> Path {
        let size = cropSize ?? rect.size
        let crop = CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2, width: size.width, height: size.height)
        var bounds = crop
        if let source, let clipFrame = source.clipFrame, scale > 0 {
            let factor = remaining / scale
            bounds = CGRect(
                x: crop.minX + (clipFrame.minX - source.frame.minX) * factor,
                y: crop.minY + (clipFrame.minY - source.frame.minY) * factor,
                width: crop.width + (clipFrame.width - source.frame.width) * factor,
                height: crop.height + (clipFrame.height - source.frame.height) * factor
            )
        }
        return RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).path(in: bounds)
    }
}

/// 불투명한 상세 화면을 하나의 창으로 이동시킨 뒤 원래 사진 카드로 접는다.
struct FeedScreenReturnEffect: ViewModifier, Animatable {
    @Environment(\.chalkakTheme) private var theme
    var progress: CGFloat
    var dragOffset: CGFloat
    let source: FeedZoomRegistry.Source?
    let photo: CGRect?
    let canvas: CGRect?
    let safeAreaInsets: EdgeInsets
    var revealsCoveredSource = false

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(progress, dragOffset) }
        set {
            progress = newValue.first
            dragOffset = newValue.second
        }
    }

    func body(content: Content) -> some View {
        let geometry: FeedScreenReturnGeometry?
        if let source, let photo, let canvas,
           photo.width > 0, photo.height > 0, source.frame.width > 0, source.frame.height > 0 {
            geometry = FeedScreenReturnGeometry(
                progress: progress, dragOffset: dragOffset, source: source, photo: photo,
                canvas: canvas, safeAreaInsets: safeAreaInsets, floatingRadius: theme.shapes.sheet,
                revealsCoveredSource: revealsCoveredSource
            )
        } else {
            geometry = nil
        }
        // 마스크 내부만 바꿔 사진 뷰와 로드된 이미지를 유지한다.
        return content
            .mask {
                if let geometry {
                    FeedScreenReturnMask(bounds: geometry.clip, screen: geometry.screen, cornerRadius: geometry.cornerRadius)
                        .clipShape(Path(geometry.viewportClip))
                } else {
                    Rectangle().ignoresSafeArea()
                }
            }
            .scaleEffect(geometry?.scale ?? 1)
            .offset(geometry?.offset ?? .zero)
    }
}

struct FeedScreenReturnGeometry {
    let scale: CGFloat
    // 출발 카드에 접힌 상태의 배율. 사진이 카드를 채우도록 정해 서명도 이 배율로 커지거나 줄어든다.
    let endScale: CGFloat
    let offset: CGSize
    let clip: CGRect
    let viewportClip: CGRect
    // 실제 화면(안전 영역 포함)을 변환 전 로컬 좌표로 옮긴 영역.
    let screen: CGRect
    let cornerRadius: CGFloat

    // 이 진행도에 이르면 하단 가장자리가 먼저 화면 끝에 닿는다.
    private static let bottomLeadProgress: CGFloat = 0.6

    init(progress: CGFloat, dragOffset: CGFloat = 0, source: FeedZoomRegistry.Source,
         photo: CGRect, canvas: CGRect, safeAreaInsets: EdgeInsets = EdgeInsets(),
         floatingRadius: CGFloat = ChalkakShapes.sheet, revealsCoveredSource: Bool = false) {
        let remaining = 1 - min(max(progress, 0), 1)
        let endScale = max(source.frame.width / photo.width, source.frame.height / photo.height)
        self.endScale = endScale
        // 드래그 중에는 최대 4%만 작아지고 전체 화면 영역을 보존한다.
        let dragScale = 1 - min(max(dragOffset, 0) / 100, 1) * 0.04
        scale = dragScale + (endScale - dragScale) * remaining
        let endOffset = CGSize(
            width: source.frame.midX - canvas.midX - (photo.midX - canvas.midX) * endScale,
            height: source.frame.midY - canvas.midY - (photo.midY - canvas.midY) * endScale
        )
        offset = CGSize(
            width: endOffset.width * remaining + dragOffset * (1 - remaining),
            height: endOffset.height * remaining
        )
        let fullBounds = CGRect(
            x: -safeAreaInsets.leading, y: -safeAreaInsets.top,
            width: canvas.width + safeAreaInsets.leading + safeAreaInsets.trailing,
            height: canvas.height + safeAreaInsets.top + safeAreaInsets.bottom
        )
        let screen = CGRect(
            x: canvas.width / 2 + (fullBounds.minX - canvas.width / 2 - offset.width) / scale,
            y: canvas.height / 2 + (fullBounds.minY - canvas.height / 2 - offset.height) / scale,
            width: fullBounds.width / scale,
            height: fullBounds.height / scale
        )
        self.screen = screen
        // 카드 모서리와 가려지는 경계는 별도로 보간한다. 교집합을 둥글게 만들면
        // 하단바 경계에 가짜 모서리가 생겼다가 원래 카드로 교체될 때 펴진다.
        func interpolatedClip(for target: CGRect) -> CGRect {
            let endClip = CGRect(
                x: canvas.width / 2 + (target.minX - canvas.midX - endOffset.width) / endScale,
                y: canvas.height / 2 + (target.minY - canvas.midY - endOffset.height) / endScale,
                width: target.width / endScale,
                height: target.height / endScale
            )
            var bounds = CGRect(
                x: fullBounds.minX + (endClip.minX - fullBounds.minX) * remaining,
                y: fullBounds.minY + (endClip.minY - fullBounds.minY) * remaining,
                width: fullBounds.width + (endClip.width - fullBounds.width) * remaining,
                height: fullBounds.height + (endClip.height - fullBounds.height) * remaining
            )
            let bottomLead = min(1, (1 - remaining) / Self.bottomLeadProgress)
                * (1 - min(max(dragOffset, 0) / 12, 1))
            let bottom = endClip.maxY + (screen.maxY - endClip.maxY) * bottomLead
            if bottom > bounds.maxY { bounds.size.height = bottom - bounds.minY }
            return bounds
        }
        clip = interpolatedClip(for: source.clipFrame ?? source.frame)
        viewportClip = source.visibleBounds.map { visible in
            var bounds = visible
            if revealsCoveredSource {
                // 복귀 사진은 바 아래까지 이동한다. 하단바 자체는 상위 레이어가 그린다.
                bounds.size.height = max(bounds.maxY, (source.clipFrame ?? source.frame).maxY) - bounds.minY
                // 교집합이 비면 CGRect.null(무한대 좌표)이 되어 마스크가 깨지므로 적용하지 않는다.
                if let container = source.containerBounds {
                    let clipped = bounds.intersection(container)
                    if !clipped.isNull { bounds = clipped }
                }
            }
            return interpolatedClip(for: bounds)
        } ?? clip
        let lift = min(1, max(max(dragOffset, 0) / 12, remaining / 0.06))
        cornerRadius = (floatingRadius * lift * (1 - remaining) + source.cornerRadius * remaining) / scale
    }
}

/// 화면 전체가 출발 카드로 접히는 동안 서명만 출발 서명의 크기·위치에서 제자리로 옮긴다.
/// 화면 배율을 그대로 받으면 사진 비율이 카드와 달라 서명이 커지거나, 잘린 사진 모서리를 따라 밀려난다.
struct FeedScreenSignatureEffect: ViewModifier, Animatable {
    var progress: CGFloat
    var dragOffset: CGFloat
    let screen: FeedScreenZoom?
    // 화면 전환이 없을 때 서명의 제자리(global, 화면 변환 전 좌표).
    let destinationFrame: CGRect?

    init(screen: FeedScreenZoom?, destinationFrame: CGRect?) {
        progress = screen?.progress ?? 1
        dragOffset = screen?.dragOffset ?? 0
        self.screen = screen
        self.destinationFrame = destinationFrame
    }

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(progress, dragOffset) }
        set {
            progress = newValue.first
            dragOffset = newValue.second
        }
    }

    func body(content: Content) -> some View {
        let placement = self.placement
        return content
            .scaleEffect(placement?.scale ?? 1, anchor: .bottomTrailing)
            .offset(placement?.offset ?? .zero)
    }

    private var placement: (scale: CGFloat, offset: CGSize)? {
        guard let screen, let destination = destinationFrame,
              let sourceSignature = screen.source.signatureFrame,
              destination.width > 0, screen.photo.width > 0, screen.photo.height > 0,
              screen.source.frame.width > 0, screen.source.frame.height > 0
        else { return nil }
        let geometry = FeedScreenReturnGeometry(
            progress: progress, dragOffset: dragOffset, source: screen.source, photo: screen.photo,
            canvas: screen.canvas, safeAreaInsets: screen.safeAreaInsets,
            revealsCoveredSource: screen.revealsCoveredSource
        )
        let remaining = 1 - min(max(progress, 0), 1)
        // 전환이 끝나면 사진과 함께 스크롤되도록 제자리에 둔다.
        guard remaining > 0 || dragOffset > 0 else { return nil }
        let endScale = geometry.endScale
        // 화면 변환 전 좌표에서 보간해, 화면 배율을 곱한 결과가 출발 서명에서 시작하게 한다.
        func interpolate(_ end: CGFloat, _ start: CGFloat) -> CGFloat {
            end + (start / endScale - end) * remaining
        }
        let sourceBounds = screen.source.clipFrame ?? screen.source.frame
        let width = interpolate(destination.width, sourceSignature.width)
        let insetX = interpolate(screen.photo.maxX - destination.maxX, sourceBounds.maxX - sourceSignature.maxX)
        let insetY = interpolate(screen.photo.maxY - destination.maxY, sourceBounds.maxY - sourceSignature.maxY)
        // 출발 카드에 가까울수록 보이는 사진 영역의 우하단을, 제자리에 가까울수록 사진 우하단을 따라간다.
        // 화면 밖으로 이어지는 긴 사진에서 서명이 화면 끝에 붙어 떠 보이지 않게 한다.
        let visible = geometry.clip.offsetBy(dx: screen.canvas.minX, dy: screen.canvas.minY)
        let corner = CGPoint(
            x: screen.photo.maxX + (min(screen.photo.maxX, visible.maxX) - screen.photo.maxX) * remaining,
            y: screen.photo.maxY + (min(screen.photo.maxY, visible.maxY) - screen.photo.maxY) * remaining
        )
        return (
            width / destination.width,
            CGSize(width: corner.x - insetX - destination.maxX, height: corner.y - insetY - destination.maxY)
        )
    }
}

private struct FeedScreenReturnMask: Shape {
    let bounds: CGRect
    let screen: CGRect
    let cornerRadius: CGFloat

    func path(in rect: CGRect) -> Path {
        // 화면 밖에 둥근 모서리를 그리면 실제 피드 가장자리는 직각으로 잘린다.
        // 이동 중에는 마스크가 실제로 그려지는 화면 경계에 라운드를 맞춘다.
        let visibleBounds = cornerRadius > 0 ? bounds.intersection(screen) : bounds
        guard !visibleBounds.isNull else { return Path() }
        return RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).path(in: visibleBounds)
    }
}

/// 시작 상태가 최소 한 화면 갱신 간격 동안 유지된 다음 확대를 시작한다.
/// Task.yield는 렌더링 경계가 아니므로 화면 갱신 신호를 사용한다.
struct FeedOpeningFrameGate: UIViewRepresentable {
    let isEnabled: Bool
    let onReady: () -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        return view
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onReady = onReady
        if isEnabled { context.coordinator.start() }
        else { context.coordinator.stop() }
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator: NSObject {
        var onReady: (() -> Void)?
        private var displayLink: CADisplayLink?
        private var hasSeenFrame = false

        func start() {
            guard displayLink == nil else { return }
            hasSeenFrame = false
            let link = CADisplayLink(target: self, selector: #selector(tick))
            displayLink = link
            link.add(to: .main, forMode: .common)
        }

        func stop() {
            displayLink?.invalidate()
            displayLink = nil
        }

        @objc private func tick() {
            guard hasSeenFrame else {
                hasSeenFrame = true
                return
            }
            stop()
            onReady?()
        }
    }
}
