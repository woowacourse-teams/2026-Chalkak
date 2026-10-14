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
    struct Source {
        let frame: CGRect
        let cornerRadius: CGFloat
        // 출발 뷰의 서명 위치. Android처럼 서명은 사진과 따로 이 위치에서 출발한다.
        var signatureFrame: CGRect?
        var visibleBounds: CGRect?
    }

    // 사진이 이동하는 동안 숨길 출발 뷰. 이동 중인 사진과 겹쳐 두 장으로 보이지 않게 한다.
    var activeSource: FeedZoomSource?
    @ObservationIgnored private var sources: [FeedZoomSource: Source] = [:]
    @ObservationIgnored private var viewports: [FeedZoomViewport: CGRect] = [:]
    @ObservationIgnored var bottomBarFrame: CGRect?

    func source(for id: FeedZoomSource) -> Source? {
        guard var source = sources[id] else { return nil }
        if let viewport = id.viewport, let bounds = viewports[viewport] {
            let bottom = bottomBarFrame.map { min(bounds.maxY, $0.minY) } ?? bounds.maxY
            source.visibleBounds = CGRect(x: bounds.minX, y: bounds.minY, width: bounds.width, height: max(0, bottom - bounds.minY))
        }
        return source
    }

    func updateViewport(_ frame: CGRect, for viewport: FeedZoomViewport) {
        viewports[viewport] = frame
    }

    func update(_ source: Source, for id: FeedZoomSource) {
        sources[id] = source
    }

    func remove(_ id: FeedZoomSource) {
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
}

extension EnvironmentValues {
    /// 없으면 줌 전환 없이 Feed가 페이드로만 열린다.
    @Entry var feedZoomRegistry: FeedZoomRegistry?
}

extension View {
    /// 이 뷰를 Feed 줌 전환의 출발 뷰로 등록한다. 사진이 이동하는 동안 이 뷰만 숨긴다.
    /// 서명이 `ChalkakSignedImage` 규칙(우하단, sm 여백)으로 올라가 있으면 그 크기를 함께 넘긴다.
    func feedZoomSource(
        _ source: FeedZoomSource,
        cornerRadius: CGFloat = 0,
        signatureSize: CGSize? = nil,
        fittedImageAspectRatio: CGFloat? = nil
    ) -> some View {
        modifier(
            FeedZoomSourceModifier(source: source, cornerRadius: cornerRadius, signatureSize: signatureSize, fittedImageAspectRatio: fittedImageAspectRatio)
        )
    }

    func feedZoomViewport(_ viewport: FeedZoomViewport) -> some View {
        modifier(FeedZoomViewportModifier(viewport: viewport))
    }

    func feedZoomViewportClip(progress: CGFloat, source: FeedZoomRegistry.Source?, destination: CGRect?, canvas: CGRect?) -> some View {
        modifier(FeedZoomViewportClipModifier(progress: progress, source: source?.visibleBounds, destination: destination, canvas: canvas))
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
    @Environment(\.chalkakTheme) private var theme
    let source: FeedZoomSource
    let cornerRadius: CGFloat
    let signatureSize: CGSize?
    let fittedImageAspectRatio: CGFloat?
    @State private var frame: CGRect?

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .global)
            } action: { newFrame in
                frame = newFrame
                register(newFrame)
            }
            // 기록처럼 같은 뷰에서 게시물만 바뀌면 위치가 그대로라 위치 변경으로는 다시 등록되지 않는다.
            .onChange(of: source) { oldSource, _ in
                registry?.remove(oldSource)
                if let frame {
                    register(frame)
                }
            }
            .onChange(of: fittedImageAspectRatio) { _, _ in
                if let frame { register(frame) }
            }
            .opacity(registry?.activeSource == source ? 0 : 1)
            .onDisappear { registry?.remove(source) }
    }

    private func register(_ frame: CGRect) {
        registry?.update(
            FeedZoomRegistry.Source(
                frame: FeedZoomCropGeometry.fittedFrame(in: frame, heightToWidthRatio: fittedImageAspectRatio),
                cornerRadius: cornerRadius,
                signatureFrame: signatureSize.map { size in
                    CGRect(
                        x: frame.maxX - theme.spacing.sm - size.width,
                        y: frame.maxY - theme.spacing.sm - size.height,
                        width: size.width,
                        height: size.height
                    )
                }
            ),
            for: source
        )
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

private struct FeedZoomViewportClipModifier: ViewModifier {
    let progress: CGFloat
    let source: CGRect?
    let destination: CGRect?
    let canvas: CGRect?

    @ViewBuilder
    func body(content: Content) -> some View {
        if let source, let destination, let canvas {
            content.clipShape(FeedZoomViewportClip(progress: progress, source: source, destination: destination, canvas: canvas))
        } else {
            content
        }
    }
}

struct FeedZoomViewportClip: Shape {
    var progress: CGFloat
    let source: CGRect
    let destination: CGRect
    let canvas: CGRect

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func path(in rect: CGRect) -> Path {
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

    @ViewBuilder
    func body(content: Content) -> some View {
        if morphsAspectRatio, let source, let destinationFrame,
           destinationFrame.width > 0, destinationFrame.height > 0, source.frame.width > 0 {
            let geometry = FeedZoomCropGeometry(progress: progress, source: source, destination: destinationFrame)
            content
                .frame(width: geometry.size.width, height: geometry.size.height)
                .clipShape(RoundedRectangle(cornerRadius: geometry.cornerRadius))
                .scaleEffect(geometry.scale)
                .offset(geometry.offset)
        } else {
            scaledContent(content)
        }
    }

    private func scaledContent(_ content: Content) -> some View {
        let remaining = 1 - progress
        let startScale = startScale
        let scale = startScale + (1 - startScale) * progress
        let startOffset = startOffset

        return content
            .clipShape(
                RoundedRectangle(cornerRadius: (source?.cornerRadius ?? 0) * remaining / scale)
            )
            .scaleEffect(scale)
            .offset(x: startOffset.width * remaining, y: startOffset.height * remaining)
            // 출발 위치를 아는데 제자리를 아직 모르면 엉뚱한 위치에 그려지므로 숨긴다.
            .opacity(source != nil && destinationFrame == nil ? 0 : 1)
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
