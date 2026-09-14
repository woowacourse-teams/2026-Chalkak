import Foundation
import ImageIO
import SwiftUI
import UIKit

struct HomeLoadedImage {
    let image: UIImage
    let heightToWidthRatio: CGFloat
}

enum HomeImagePipelineError: Error {
    case invalidResponse
    case invalidImage
    case decodeFailed
}

actor HomeImagePipeline {
    static let shared = HomeImagePipeline()

    private final class CacheEntry: NSObject {
        let value: HomeLoadedImage

        init(value: HomeLoadedImage) {
            self.value = value
        }
    }

    private let session: URLSession
    private let cache = NSCache<NSString, CacheEntry>()
    private var inFlight: [String: Task<HomeLoadedImage, Error>] = [:]

    init(session: URLSession = .shared) {
        self.session = session
        cache.totalCostLimit = 96 * 1_024 * 1_024
        cache.countLimit = 60
    }

    func image(for url: URL, targetPixelWidth: Int) async throws -> HomeLoadedImage {
        let normalizedWidth = max(targetPixelWidth, 1)
        let key = "\(url.absoluteString)#\(normalizedWidth)"

        if let cached = cache.object(forKey: key as NSString) {
            return cached.value
        }
        if let existingTask = inFlight[key] {
            return try await existingTask.value
        }

        let session = session
        let task = Task {
            try await Self.download(
                from: url,
                targetPixelWidth: normalizedWidth,
                session: session
            )
        }
        inFlight[key] = task

        do {
            let loadedImage = try await task.value
            cache.setObject(
                CacheEntry(value: loadedImage),
                forKey: key as NSString,
                cost: Self.memoryCost(of: loadedImage.image)
            )
            inFlight[key] = nil
            return loadedImage
        } catch {
            inFlight[key] = nil
            throw error
        }
    }

    private static func download(
        from url: URL,
        targetPixelWidth: Int,
        session: URLSession
    ) async throws -> HomeLoadedImage {
        let (data, response) = try await session.data(from: url)
        try Task.checkCancellation()

        if let httpResponse = response as? HTTPURLResponse,
           !(200..<300).contains(httpResponse.statusCode) {
            throw HomeImagePipelineError.invalidResponse
        }
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            throw HomeImagePipelineError.invalidImage
        }

        let ratio = try imageRatio(from: source)
        let targetMaxPixelSize = CGFloat(targetPixelWidth) * max(1, ratio)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: targetMaxPixelSize,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(
            source,
            0,
            options as CFDictionary
        ) else {
            throw HomeImagePipelineError.decodeFailed
        }

        return HomeLoadedImage(
            image: UIImage(cgImage: cgImage),
            heightToWidthRatio: ratio
        )
    }

    private static func imageRatio(from source: CGImageSource) throws -> CGFloat {
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)
            as? [CFString: Any],
            let pixelWidth = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
            let pixelHeight = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue,
            pixelWidth > 0,
            pixelHeight > 0
        else {
            throw HomeImagePipelineError.invalidImage
        }

        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        let isRotated = (5...8).contains(orientation)
        let width = isRotated ? pixelHeight : pixelWidth
        let height = isRotated ? pixelWidth : pixelHeight
        return CGFloat(height / width)
    }

    private static func memoryCost(of image: UIImage) -> Int {
        guard let cgImage = image.cgImage else { return 0 }
        return cgImage.bytesPerRow * cgImage.height
    }
}

struct HomeRemoteMeasuredImage: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.displayScale) private var displayScale

    let url: URL?
    let contentDescription: String?
    let onRatioLoaded: (CGFloat?) -> Void

    @State private var image: UIImage?
    @State private var isLoading = true
    @State private var didFail = false

    var body: some View {
        GeometryReader { proxy in
            imageContent
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()
                .loadingSkeleton(isLoading: isLoading)
                .task(id: loadID(width: proxy.size.width)) {
                    await load(width: proxy.size.width)
                }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(contentDescription ?? "")
        .accessibilityHidden(contentDescription == nil)
    }

    @ViewBuilder
    private var imageContent: some View {
        if let image {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if didFail || url == nil {
            ZStack {
                theme.colors.inputBackground
                Image(systemName: didFail ? "photo.badge.exclamationmark" : "photo")
                    .font(.system(size: Metrics.placeholderIconSize))
                    .foregroundStyle(theme.colors.iconSecondary)
            }
        } else {
            Color.clear
        }
    }

    private func loadID(width: CGFloat) -> String {
        guard let url else { return "missing" }
        guard width > 0 else { return "pending" }
        return "\(url.absoluteString)#\(targetPixelWidth(for: width))"
    }

    private func targetPixelWidth(for width: CGFloat) -> Int {
        max(Int((width * displayScale).rounded(.up)), 1)
    }

    @MainActor
    private func load(width: CGFloat) async {
        guard width > 0 else { return }
        guard let url else {
            image = nil
            isLoading = false
            didFail = false
            onRatioLoaded(nil)
            return
        }

        image = nil
        isLoading = true
        didFail = false
        onRatioLoaded(nil)
        do {
            let loadedImage = try await HomeImagePipeline.shared.image(
                for: url,
                targetPixelWidth: targetPixelWidth(for: width)
            )
            try Task.checkCancellation()

            var transaction = Transaction()
            transaction.animation = nil
            withTransaction(transaction) {
                onRatioLoaded(loadedImage.heightToWidthRatio)
                image = loadedImage.image
                isLoading = false
            }
        } catch is CancellationError {
            return
        } catch {
            isLoading = false
            didFail = true
            onRatioLoaded(nil)
        }
    }
}

private enum Metrics {
    static let placeholderIconSize: CGFloat = 24
}
