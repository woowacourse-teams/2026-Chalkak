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
    /// - Parameter maxPixelWidth: 원격 이미지를 받을 때 지정한 최대 폭. 같은 폭으로 받아 둔 이미지만 꺼낸다.
    ///   nil이면 기본 폭(화면 폭) 이미지를 찾는다.
    func cachedImage(maxPixelWidth: CGFloat? = nil) -> UIImage? {
        switch self {
        case let .asset(name):
            UIImage(named: name)
        case .system:
            nil
        case let .remote(url?):
            RemoteImageCache.shared.image(for: url, maxPixelWidth: maxPixelWidth)
        case .remote(nil):
            nil
        }
    }

    /// 이 이미지를 어떤 크기로든 받아 둔 적이 있으면 그중 가장 큰 것을 꺼낸다.
    /// 목록 칸이 칸 크기로 받아 둔 썸네일을 피드 전환 첫 프레임에 그대로 쓸 때 쓴다.
    func largestCachedImage() -> UIImage? {
        switch self {
        case let .remote(url?):
            RemoteImageCache.shared.largestImage(for: url)
        default:
            cachedImage()
        }
    }
}

/// 받아 둔 원격 이미지를 메모리에 보관한다.
/// 같은 이미지를 다른 화면에서 다시 그릴 때 네트워크·디코딩을 기다리지 않고 첫 프레임부터 보여준다.
///
/// 같은 URL이라도 디코딩한 크기가 다르면 따로 보관한다.
/// 예: 기록 달력 칸은 작은 폭으로, 그 아래 선택 사진은 기본 폭으로 받아 각각 캐시한다.
///
/// 디코딩 크기는 둘 중 하나로 정한다.
/// - `maxPixelWidth`: 폭만 본다. 사진 비율대로 칸이 늘어나는 곳(전시 그리드, 기록 달력)에 쓴다.
/// - `fillPixelSize`: 이미지가 그 크기를 덮을 만큼만 줄인다. 정사각 칸을 꽉 채워 잘라 그리는 곳에 쓴다.
///   가로 사진도 높이가 칸보다 작아지지 않아 흐려지지 않는다.
final class RemoteImageCache {
    static let shared = RemoteImageCache()

    private let cache = NSCache<NSString, UIImage>()
    private var inFlight: [String: Task<UIImage, Error>] = [:]
    // 같은 URL을 여러 폭으로 동시에 요청해도 네트워크로는 한 번만 받는다.
    private var inFlightData: [URL: Task<Data, Error>] = [:]
    // URL마다 캐시에 넣은 키. 어떤 크기로든 받아 둔 이미지를 찾을 때 쓴다. 밀려난 키는 찾을 때 건너뛴다.
    private var keysByURL: [URL: Set<String>] = [:]
    private let maxPixelWidth: CGFloat

    /// - Parameter maxPixelWidth: 이보다 넓은 이미지는 이 폭에 맞춰 줄여서 푼다.
    ///   앱은 사진을 화면 폭보다 크게 보여주지 않으므로 가장 넓은 iPhone 화면 폭을 기본으로 한다.
    ///   3024×4032 원본을 그대로 풀면 한 장이 약 46MB라 캐시가 원본 한두 장으로 가득 차 다른 이미지가 밀려난다.
    init(totalCostLimit: Int = 64 * 1_024 * 1_024, maxPixelWidth: CGFloat = 1_320) {
        cache.totalCostLimit = totalCostLimit
        self.maxPixelWidth = maxPixelWidth
    }

    /// - Parameter maxPixelWidth: `load(_:maxPixelWidth:)`에 넘긴 값과 같아야 찾는다. nil이면 기본 폭.
    func image(for url: URL, maxPixelWidth: CGFloat? = nil) -> UIImage? {
        image(for: url, fillPixelSize: Self.widthOnlySize(maxPixelWidth))
    }

    /// - Parameter fillPixelSize: `load(_:fillPixelSize:)`에 넘긴 값과 같아야 찾는다. nil이면 기본 폭.
    func image(for url: URL, fillPixelSize: CGSize?) -> UIImage? {
        cache.object(forKey: cacheKey(for: url, fillPixelSize: fillPixelSize) as NSString)
    }

    /// 이 URL을 어떤 크기로든 받아 둔 이미지 중 가장 큰 것을 꺼낸다. 없으면 nil.
    func largestImage(for url: URL) -> UIImage? {
        (keysByURL[url] ?? [])
            .compactMap { cache.object(forKey: $0 as NSString) }
            .max { $0.memoryCost < $1.memoryCost }
    }

    /// 이미지를 받아 `maxPixelWidth` 폭 이하로 디코딩하고 캐시한다.
    /// - Parameter maxPixelWidth: 화면에 작게 그리는 이미지는 그 크기에 맞는 폭을 넘겨 캐시 용량을 아낀다.
    ///   nil이면 init에서 정한 기본 폭(화면 폭)을 쓴다.
    func load(_ url: URL, maxPixelWidth: CGFloat? = nil) async throws -> UIImage {
        try await load(url, fillPixelSize: Self.widthOnlySize(maxPixelWidth))
    }

    /// 이미지를 받아 `fillPixelSize`를 덮을 만큼만 줄여 디코딩하고 캐시한다. 원래보다 키우지는 않는다.
    /// - Parameter fillPixelSize: 높이가 0이면 폭만 본다. nil이면 init에서 정한 기본 폭(화면 폭)을 쓴다.
    func load(_ url: URL, fillPixelSize: CGSize?) async throws -> UIImage {
        let key = cacheKey(for: url, fillPixelSize: fillPixelSize)
        if let cached = cache.object(forKey: key as NSString) {
            return cached
        }
        if let task = inFlight[key] {
            return try await task.value
        }

        let fillPixelSize = fillPixelSize ?? CGSize(width: maxPixelWidth, height: 0)
        let task = Task {
            let data = try await self.data(for: url)
            // 화면에 붙이는 순간 메인 스레드에서 디코딩하지 않도록 백그라운드에서 미리 풀어 둔다.
            let image = await Task.detached(priority: .userInitiated) {
                Self.decodedImage(from: data, fillPixelSize: fillPixelSize)
            }.value
            guard let image else {
                throw URLError(.cannotDecodeContentData)
            }
            return image
        }
        inFlight[key] = task
        defer { inFlight[key] = nil }

        let image = try await task.value
        cache.setObject(image, forKey: key as NSString, cost: image.memoryCost)
        keysByURL[url, default: []].insert(key)
        return image
    }

    private func data(for url: URL) async throws -> Data {
        if let task = inFlightData[url] {
            return try await task.value
        }

        let task = Task {
            let (data, response) = try await URLSession.shared.data(from: url)
            if let response = response as? HTTPURLResponse,
               !(200..<300).contains(response.statusCode) {
                throw URLError(.badServerResponse)
            }
            return data
        }
        inFlightData[url] = task
        defer { inFlightData[url] = nil }

        return try await task.value
    }

    /// 기본 폭은 URL만, 폭만 정하면 `폭|URL`, 덮을 크기를 정하면 `폭x높이|URL` 형태로 만들어 크기별로 따로 보관한다.
    private func cacheKey(for url: URL, fillPixelSize: CGSize?) -> String {
        guard let fillPixelSize,
              fillPixelSize != CGSize(width: maxPixelWidth, height: 0)
        else {
            return url.absoluteString
        }
        if fillPixelSize.height <= 0 {
            return "\(Int(fillPixelSize.width))|\(url.absoluteString)"
        }
        return "\(Int(fillPixelSize.width))x\(Int(fillPixelSize.height))|\(url.absoluteString)"
    }

    private static func widthOnlySize(_ maxPixelWidth: CGFloat?) -> CGSize? {
        maxPixelWidth.map { CGSize(width: $0, height: 0) }
    }
}

extension RemoteImageCache {
    /// EXIF 방향을 반영해, 폭이 `maxPixelWidth`를 넘으면 그 폭에 맞춰 줄인 비트맵으로 디코딩한다.
    nonisolated static func decodedImage(from data: Data, maxPixelWidth: CGFloat) -> UIImage? {
        decodedImage(from: data, fillPixelSize: CGSize(width: maxPixelWidth, height: 0))
    }

    /// EXIF 방향을 반영해, 이미지가 `fillPixelSize`를 덮는 데까지만 줄인 비트맵으로 디코딩한다.
    /// 높이(또는 폭)가 0이면 그 축은 보지 않는다. 원래 크기보다 키우지 않는다.
    nonisolated static func decodedImage(from data: Data, fillPixelSize: CGSize) -> UIImage? {
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
        let displayHeight = isRotated ? pixelWidth : pixelHeight
        let widthScale = Double(fillPixelSize.width) / displayWidth
        let heightScale = Double(fillPixelSize.height) / displayHeight
        let fillScale = max(widthScale, heightScale)
        let scale = fillScale > 0 ? min(1, fillScale) : 1
        // 810×1080을 폭 246에 맞출 때처럼 나누어떨어지는 값이 부동소수 오차로 1px 커지지 않게 조금 내린다.
        let maxPixelSize = (max(pixelWidth, pixelHeight) * scale - 0.001).rounded(.up)

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
    /// - Parameter maxPixelWidth: 같은 사진을 칸 폭으로 그리는 화면은 같은 폭을 넘겨, 비율을 재느라 큰 이미지를 따로 받지 않게 한다.
    static func ratio(for source: ChalkakImageSource, maxPixelWidth: CGFloat? = nil) async -> CGFloat? {
        switch source {
        case let .asset(name):
            guard let image = UIImage(named: name), image.size.width > 0 else { return nil }
            return image.size.height / image.size.width
        case .system:
            return nil
        case let .remote(url?):
            // 화면에 그리는 요청과 같은 캐시·진행 중 요청을 써서 같은 이미지를 두 번 받지 않는다.
            // 캐시는 EXIF 방향을 반영해 비율을 유지한 채 줄이므로 비율이 그대로다.
            guard let image = try? await RemoteImageCache.shared.load(url, maxPixelWidth: maxPixelWidth),
                  image.size.width > 0
            else { return nil }
            return image.size.height / image.size.width
        case .remote(nil):
            return nil
        }
    }
}

extension ChalkakImage {
    /// 칸 폭(pt)을 화면 배율로 그리는 데 필요한 픽셀 폭. 칸 폭을 아직 모르면(0) nil이라 기본 폭으로 받는다.
    static func pixelWidth(forPointWidth pointWidth: CGFloat, displayScale: CGFloat) -> CGFloat? {
        guard pointWidth > 0, displayScale > 0 else { return nil }
        return (pointWidth * displayScale).rounded(.up)
    }

    /// 칸 크기(pt)를 화면 배율로 그리는 데 필요한 픽셀 크기. 칸 크기를 아직 모르면 nil이다.
    static func pixelSize(forPointSize pointSize: CGSize, displayScale: CGFloat) -> CGSize? {
        guard let width = pixelWidth(forPointWidth: pointSize.width, displayScale: displayScale),
              let height = pixelWidth(forPointWidth: pointSize.height, displayScale: displayScale)
        else { return nil }
        return CGSize(width: width, height: height)
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
    // 원격 이미지를 이 폭(픽셀) 이하로 줄여 디코딩·캐시한다. 작은 칸에 그릴 때 메모리를 아낀다.
    // nil이면 기본 폭(화면 폭)을 쓴다.
    var maxPixelWidth: CGFloat?
    // 원격 이미지를 이 크기(픽셀)를 덮을 만큼만 줄여 디코딩·캐시한다. 정사각 칸을 꽉 채워 그릴 때 쓴다.
    // 넘기면 `maxPixelWidth`보다 우선한다.
    var fillPixelSize: CGSize?
    // `thumbnailSource`를 디코딩할 최대 폭. 다른 화면이 작은 폭으로 받아 둔 이미지를 썸네일로 재사용할 때 쓴다.
    var thumbnailMaxPixelWidth: CGFloat?
    @State private var isPrimaryLoaded = false

    var body: some View {
        ZStack {
            if let thumbnailSource, !isPrimaryShown {
                image(
                    for: thumbnailSource,
                    fillPixelSize: thumbnailMaxPixelWidth.map { CGSize(width: $0, height: 0) },
                    showsLoadingSkeleton: true,
                    onLoad: {}
                )
            }
            image(
                for: source,
                fillPixelSize: primaryFillPixelSize,
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
        guard !isPrimaryLoaded else { return true }
        guard case let .remote(url?) = source else { return source.cachedImage() != nil }
        return RemoteImageCache.shared.image(for: url, fillPixelSize: primaryFillPixelSize) != nil
    }

    private var primaryFillPixelSize: CGSize? {
        fillPixelSize ?? maxPixelWidth.map { CGSize(width: $0, height: 0) }
    }

    @ViewBuilder
    private func image(
        for source: ChalkakImageSource,
        fillPixelSize: CGSize?,
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
                fillPixelSize: fillPixelSize,
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
    let fillPixelSize: CGSize?
    let contentMode: ContentMode
    let showsLoadingSkeleton: Bool
    let onLoad: () -> Void
    @State private var phase: RemoteImagePhase

    init(
        url: URL?,
        fillPixelSize: CGSize?,
        contentMode: ContentMode,
        showsLoadingSkeleton: Bool,
        onLoad: @escaping () -> Void
    ) {
        self.url = url
        self.fillPixelSize = fillPixelSize
        self.contentMode = contentMode
        self.showsLoadingSkeleton = showsLoadingSkeleton
        self.onLoad = onLoad
        _phase = State(
            initialValue: url
                .flatMap { RemoteImageCache.shared.image(for: $0, fillPixelSize: fillPixelSize) }
                .map(RemoteImagePhase.success)
                ?? .loading
        )
    }

    var body: some View {
        if let url {
            phaseContent
                .loadingSkeleton(isLoading: phase.isLoading && showsLoadingSkeleton)
                // 칸 크기가 바뀌면 그 크기로 다시 받는다.
                .task(id: LoadID(url: url, fillPixelSize: fillPixelSize)) { await load(url) }
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
        if let cached = RemoteImageCache.shared.image(for: url, fillPixelSize: fillPixelSize) {
            phase = .success(cached)
            onLoad()
            return
        }

        phase = .loading
        do {
            let image = try await RemoteImageCache.shared.load(url, fillPixelSize: fillPixelSize)
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

private struct LoadID: Equatable {
    let url: URL
    let fillPixelSize: CGSize?
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
