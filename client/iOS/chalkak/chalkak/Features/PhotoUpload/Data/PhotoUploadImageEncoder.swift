import Foundation
import ImageIO
import SDWebImageWebPCoder
import UIKit

enum PhotoUploadImageEncodingError: Error, Equatable, Sendable {
    case invalidSource
    case unsupported
    case encodeFailed
    case sizeLimitExceeded
}

enum PhotoUploadImageEncoder {
    static func encode(sourceData: Data, maxBytes: Int64) async throws -> Data {
        try Task.checkCancellation()
        guard maxBytes > 0, sourceData.isEmpty == false else {
            throw PhotoUploadImageEncodingError.invalidSource
        }
        let encodingTask = Task.detached(priority: .userInitiated) {
            Result { try encodeOnBackgroundThread(sourceData: sourceData, maxBytes: maxBytes) }
        }
        // Match Android: let blocking conversion finish, then discard a cancelled result.
        let result = await encodingTask.value
        try Task.checkCancellation()
        return try result.get()
    }

    private nonisolated static func encodeOnBackgroundThread(
        sourceData: Data,
        maxBytes: Int64
    ) throws -> Data {
        guard let source = CGImageSourceCreateWithData(sourceData as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0
        else {
            throw PhotoUploadImageEncodingError.invalidSource
        }

        let longEdge = max(width, height)
        var sampleSize = 1
        while longEdge / sampleSize > Constants.initialMaxLongEdge {
            sampleSize *= 2
        }
        let sampledLongEdge = max(1, Int(ceil(Double(longEdge) / Double(sampleSize))))
        guard let image = CGImageSourceCreateThumbnailAtIndex(
            source,
            0,
            [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: sampledLongEdge,
            ] as CFDictionary
        )
        else {
            throw PhotoUploadImageEncodingError.invalidSource
        }

        var candidate = UIImage(cgImage: image, scale: 1, orientation: .up)

        for round in 0...Constants.maxRescaleRounds {
            for quality in Constants.qualityLadder {
                guard let encoded = SDImageWebPCoder.shared.encodedData(
                    with: candidate,
                    format: .webP,
                    options: [
                        .encodeCompressionQuality: quality,
                        .encodeWebPMethod: Constants.encodingMethod,
                    ]
                ) else {
                    throw PhotoUploadImageEncodingError.encodeFailed
                }

                guard isWebP(encoded) else {
                    throw PhotoUploadImageEncodingError.unsupported
                }
                if Int64(encoded.count) <= maxBytes {
                    return encoded
                }
            }

            if round < Constants.maxRescaleRounds {
                let size = CGSize(
                    width: max(1, (candidate.size.width * Constants.rescaleFactor).rounded()),
                    height: max(1, (candidate.size.height * Constants.rescaleFactor).rounded())
                )
                guard size != candidate.size else {
                    throw PhotoUploadImageEncodingError.sizeLimitExceeded
                }
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1
                candidate = UIGraphicsImageRenderer(size: size, format: format).image { _ in
                    candidate.draw(in: CGRect(origin: .zero, size: size))
                }
            }
        }

        throw PhotoUploadImageEncodingError.sizeLimitExceeded
    }

    private nonisolated static func isWebP(_ data: Data) -> Bool {
        guard data.count >= 12 else { return false }
        return data.prefix(4) == Data("RIFF".utf8)
            && data.subdata(in: 8..<12) == Data("WEBP".utf8)
    }

    private enum Constants {
        static let initialMaxLongEdge = 4_096
        static let maxRescaleRounds = 3
        static let qualityLadder = [0.9, 0.8, 0.6, 0.4]
        static let encodingMethod = 0
        static let rescaleFactor = 0.85
    }
}
