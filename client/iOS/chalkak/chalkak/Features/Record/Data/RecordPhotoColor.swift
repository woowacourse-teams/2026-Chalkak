import CoreGraphics
import Foundation
import ImageIO
import UIKit

/// 연간 달력 칸에 칠하는 사진의 대표색(0...1 RGB).
nonisolated struct RecordPhotoColor: Codable, Hashable, Sendable {
    let red: Double
    let green: Double
    let blue: Double
}

/// 썸네일을 아주 작게 줄여 평균색을 구하고, 칸에서 너무 탁해 보이지 않게 채도를 조금 올린다.
nonisolated enum RecordPhotoColorExtractor {
    private static let sampleSize = 8
    private static let saturationBoost: CGFloat = 1.2

    static func color(from data: Data) -> RecordPhotoColor? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: sampleSize * 4,
            kCGImageSourceCreateThumbnailWithTransform: true
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return color(from: image)
    }

    static func color(from image: CGImage) -> RecordPhotoColor? {
        let size = sampleSize
        var pixels = [UInt8](repeating: 0, count: size * size * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: size,
                height: size,
                bitsPerComponent: 8,
                bytesPerRow: size * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.interpolationQuality = .medium
            context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
            return true
        }
        guard drawn else { return nil }

        var red = 0.0, green = 0.0, blue = 0.0, weight = 0.0
        for index in stride(from: 0, to: pixels.count, by: 4) {
            let alpha = Double(pixels[index + 3]) / 255
            guard alpha > 0 else { continue }
            // premultiplied 값이므로 그대로 더하고 알파 합으로 나누면 투명 영역을 빼고 평균이 된다.
            red += Double(pixels[index]) / 255
            green += Double(pixels[index + 1]) / 255
            blue += Double(pixels[index + 2]) / 255
            weight += alpha
        }
        guard weight > 0 else { return nil }

        let average = UIColor(red: red / weight, green: green / weight, blue: blue / weight, alpha: 1)
        var hue: CGFloat = 0, saturation: CGFloat = 0, brightness: CGFloat = 0, alpha: CGFloat = 0
        average.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha)
        let boosted = UIColor(
            hue: hue,
            saturation: min(1, saturation * saturationBoost),
            brightness: brightness,
            alpha: 1
        )
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0
        boosted.getRed(&r, green: &g, blue: &b, alpha: &alpha)
        return RecordPhotoColor(red: Double(r), green: Double(g), blue: Double(b))
    }
}

/// 사진 색은 바뀌지 않으므로 게시물 ID별로 기기에 저장해 두고 다시 내려받지 않는다.
actor RecordPhotoColorCache {
    static let shared = RecordPhotoColorCache()

    private var colors: [String: RecordPhotoColor]?
    private let fileURL: URL? = FileManager.default
        .urls(for: .cachesDirectory, in: .userDomainMask)
        .first?
        .appendingPathComponent("record-photo-colors.json")

    func color(for postID: String) -> RecordPhotoColor? {
        loadedColors()[postID]
    }

    func store(_ color: RecordPhotoColor, for postID: String) {
        var colors = loadedColors()
        colors[postID] = color
        self.colors = colors
        guard let fileURL, let data = try? JSONEncoder().encode(colors) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private func loadedColors() -> [String: RecordPhotoColor] {
        if let colors { return colors }
        let loaded = fileURL
            .flatMap { try? Data(contentsOf: $0) }
            .flatMap { try? JSONDecoder().decode([String: RecordPhotoColor].self, from: $0) }
            ?? [:]
        colors = loaded
        return loaded
    }
}

enum RecordPhotoColorLoader {
    /// 저장된 색이 있으면 쓰고, 없으면 썸네일을 내려받아 색을 구한 뒤 저장한다.
    nonisolated static func color(for post: RecordPost) async -> RecordPhotoColor? {
        if let cached = await RecordPhotoColorCache.shared.color(for: post.postId) {
            return cached
        }

        let color: RecordPhotoColor?
        switch post.thumbnailImageSource {
        case let .remote(url?):
            guard let (data, response) = try? await URLSession.shared.data(from: url),
                  let response = response as? HTTPURLResponse,
                  (200..<300).contains(response.statusCode)
            else { return nil }
            color = RecordPhotoColorExtractor.color(from: data)
        case let .asset(name):
            color = await MainActor.run { UIImage(named: name)?.cgImage }
                .flatMap(RecordPhotoColorExtractor.color(from:))
        case .remote(nil), .system:
            color = nil
        }

        if let color {
            await RecordPhotoColorCache.shared.store(color, for: post.postId)
        }
        return color
    }
}
