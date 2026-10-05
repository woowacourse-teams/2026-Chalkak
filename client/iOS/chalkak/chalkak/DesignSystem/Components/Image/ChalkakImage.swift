import ImageIO
import SwiftUI
import UIKit

enum ChalkakImageSource: Hashable, Sendable {
    case asset(String)
    case system(String)
    case remote(URL?)
}

extension ChalkakImageSource {
    /// 이미 받아 둔 이미지를 동기로 꺼낸다. 원격 이미지는 앱이 한 번 그린 적이 있을 때만 반환한다.
    func cachedImage() -> UIImage? {
        switch self {
        case let .asset(name):
            UIImage(named: name)
        case .system:
            nil
        case let .remote(url?):
            RemoteImageCache.shared.image(for: url)
        case .remote(nil):
            nil
        }
    }
}

/// 받아 둔 원격 이미지를 메모리에 보관한다.
/// 같은 이미지를 다른 화면에서 다시 그릴 때 네트워크·디코딩을 기다리지 않고 첫 프레임부터 보여준다.
final class RemoteImageCache {
    static let shared = RemoteImageCache()

    private let cache = NSCache<NSURL, UIImage>()
    private var inFlight: [URL: Task<UIImage, Error>] = [:]
    private let maxPixelWidth: CGFloat

    /// - Parameter maxPixelWidth: 이보다 넓은 이미지는 이 폭에 맞춰 줄여서 푼다.
    ///   앱은 사진을 화면 폭보다 크게 보여주지 않으므로 가장 넓은 iPhone 화면 폭을 기본으로 한다.
    ///   3024×4032 원본을 그대로 풀면 한 장이 약 46MB라 캐시가 원본 한두 장으로 가득 차 다른 이미지가 밀려난다.
    init(totalCostLimit: Int = 64 * 1_024 * 1_024, maxPixelWidth: CGFloat = 1_320) {
        cache.totalCostLimit = totalCostLimit
        self.maxPixelWidth = maxPixelWidth
    }

    func image(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func load(_ url: URL) async throws -> UIImage {
        if let cached = image(for: url) {
            return cached
        }
        if let task = inFlight[url] {
            return try await task.value
        }

        let maxPixelWidth = maxPixelWidth
        let task = Task {
            let (data, response) = try await URLSession.shared.data(from: url)
            if let response = response as? HTTPURLResponse,
               !(200..<300).contains(response.statusCode) {
                throw URLError(.badServerResponse)
            }
            // 화면에 붙이는 순간 메인 스레드에서 디코딩하지 않도록 백그라운드에서 미리 풀어 둔다.
            let image = await Task.detached(priority: .userInitiated) {
                Self.decodedImage(from: data, maxPixelWidth: maxPixelWidth)
            }.value
            guard let image else {
                throw URLError(.cannotDecodeContentData)
            }
            return image
        }
        inFlight[url] = task
        defer { inFlight[url] = nil }

        let image = try await task.value
        cache.setObject(image, forKey: url as NSURL, cost: image.memoryCost)
        return image
    }
}

extension RemoteImageCache {
    /// EXIF 방향을 반영해, 폭이 `maxPixelWidth`를 넘으면 그 폭에 맞춰 줄인 비트맵으로 디코딩한다.
    nonisolated static func decodedImage(from data: Data, maxPixelWidth: CGFloat) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let pixelWidth = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
              let pixelHeight = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue,
              pixelWidth > 0, pixelHeight > 0
        else {
            return UIImage(data: data)
        }

        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        let isRotated = (5...8).contains(orientation)
        let displayWidth = isRotated ? pixelHeight : pixelWidth
        let scale = min(1, Double(maxPixelWidth) / displayWidth)
        let maxPixelSize = (max(pixelWidth, pixelHeight) * scale).rounded(.up)

        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return UIImage(data: data)
        }
        return UIImage(cgImage: cgImage)
    }
}

private extension UIImage {
    var memoryCost: Int {
        Int(size.width * scale * size.height * scale * 4)
    }
}

/// 이미지의 EXIF 방향을 반영한 세로/가로 비율(height / width)을 구한다.
enum ImageRatioLoader {
    static func ratio(for source: ChalkakImageSource) async -> CGFloat? {
        switch source {
        case let .asset(name):
            guard let image = UIImage(named: name), image.size.width > 0 else { return nil }
            return image.size.height / image.size.width
        case .system:
            return nil
        case let .remote(url?):
            // 화면에 그리는 요청과 같은 캐시·진행 중 요청을 써서 같은 이미지를 두 번 받지 않는다.
            // 캐시는 EXIF 방향을 반영해 비율을 유지한 채 줄이므로 비율이 그대로다.
            guard let image = try? await RemoteImageCache.shared.load(url),
                  image.size.width > 0
            else { return nil }
            return image.size.height / image.size.width
        case .remote(nil):
            return nil
        }
    }
}

struct ChalkakImage: View {
    @Environment(\.chalkakTheme) private var theme
    let source: ChalkakImageSource
    var contentDescription: String?
    var contentMode: ContentMode = .fill
    // 호출부가 로딩 중 자리를 다른 이미지로 채울 때 스켈레톤을 끈다.
    var showsLoadingSkeleton = true
    // 이미지가 화면에 그려질 준비가 됐을 때 호출된다.
    var onLoad: () -> Void = {}
    // 이미지를 받는 동안 대신 보여줄 썸네일. Android ChalkakImage의 thumbnailModel과 같다.
    var thumbnailSource: ChalkakImageSource?
    @State private var isPrimaryLoaded = false

    var body: some View {
        ZStack {
            if let thumbnailSource, !isPrimaryShown {
                image(for: thumbnailSource, showsLoadingSkeleton: true, onLoad: {})
            }
            image(
                for: source,
                showsLoadingSkeleton: showsLoadingSkeleton && thumbnailSource == nil,
                onLoad: {
                    isPrimaryLoaded = true
                    onLoad()
                }
            )
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(contentDescription ?? "")
        .accessibilityHidden(contentDescription == nil)
    }

    // 이미 받아 둔 이미지는 첫 프레임부터 그려지므로 썸네일을 겹쳐 그리지 않는다.
    private var isPrimaryShown: Bool {
        isPrimaryLoaded || source.cachedImage() != nil
    }

    @ViewBuilder
    private func image(
        for source: ChalkakImageSource,
        showsLoadingSkeleton: Bool,
        onLoad: @escaping () -> Void
    ) -> some View {
        switch source {
        case let .asset(name):
            Image(name)
                .resizable()
                .aspectRatio(contentMode: contentMode)
                .onAppear(perform: onLoad)
        case let .system(name):
            Image(systemName: name)
                .resizable()
                .aspectRatio(contentMode: contentMode)
                .foregroundStyle(theme.colors.iconSecondary)
        case let .remote(url):
            RemoteImage(
                url: url,
                contentMode: contentMode,
                showsLoadingSkeleton: showsLoadingSkeleton,
                onLoad: onLoad
            )
        }
    }
}

/// 원격 이미지를 로드하며, 로딩 지연·최소 표시 규칙에 따라 스켈레톤을 노출한다.
/// 이미 받아 둔 이미지는 첫 프레임부터 바로 그린다.
private struct RemoteImage: View {
    @Environment(\.chalkakTheme) private var theme
    let url: URL?
    let contentMode: ContentMode
    let showsLoadingSkeleton: Bool
    let onLoad: () -> Void
    @State private var phase: RemoteImagePhase

    init(
        url: URL?,
        contentMode: ContentMode,
        showsLoadingSkeleton: Bool,
        onLoad: @escaping () -> Void
    ) {
        self.url = url
        self.contentMode = contentMode
        self.showsLoadingSkeleton = showsLoadingSkeleton
        self.onLoad = onLoad
        _phase = State(
            initialValue: url.flatMap(RemoteImageCache.shared.image(for:)).map(RemoteImagePhase.success)
                ?? .loading
        )
    }

    var body: some View {
        if let url {
            phaseContent
                .loadingSkeleton(isLoading: phase.isLoading && showsLoadingSkeleton)
                .task(id: url) { await load(url) }
        } else {
            // URL이 없으면 로드가 끝나지 않으므로 스켈레톤 대신 고정 플레이스홀더를 표시한다.
            imagePlaceholder(systemName: "photo")
        }
    }

    @ViewBuilder
    private var phaseContent: some View {
        switch phase {
        case let .success(image):
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        case .failure:
            imagePlaceholder(systemName: "photo.badge.exclamationmark")
        case .loading:
            Color.clear
        }
    }

    private func load(_ url: URL) async {
        if let cached = RemoteImageCache.shared.image(for: url) {
            phase = .success(cached)
            onLoad()
            return
        }

        phase = .loading
        do {
            let image = try await RemoteImageCache.shared.load(url)
            phase = .success(image)
            onLoad()
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            phase = .failure
        }
    }

    private func imagePlaceholder(systemName: String) -> some View {
        ZStack {
            theme.colors.inputBackground
            Image(systemName: systemName)
                .font(.system(size: Metrics.placeholderIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
        }
    }
}

private enum RemoteImagePhase {
    case loading
    case success(UIImage)
    case failure

    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
}

private enum Metrics {
    static let placeholderIconSize: CGFloat = 24
}

#Preview("Image", traits: .sizeThatFitsLayout) {
    ChalkakImage(
        source: .asset("preview_photo"),
        contentDescription: "전시 사진",
        contentMode: .fit
    )
    .frame(width: 270, height: 360)
    .background(ChalkakTheme.light.colors.inputBackground)
}
