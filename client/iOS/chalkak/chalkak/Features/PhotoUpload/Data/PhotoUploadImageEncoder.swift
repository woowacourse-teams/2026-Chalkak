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
        guard maxBytes > 0, sourceData.isEmpty == false else {
            throw PhotoUploadImageEncodingError.invalidSource
        }

        return try await Task.detached(priority: .userInitiated) {
            try encodeOnBackgroundThread(sourceData: sourceData, maxBytes: maxBytes)
        }.value
    }

    private nonisolated static func encodeOnBackgroundThread(
        sourceData: Data,
        maxBytes: Int64
    ) throws -> Data {
        guard let source = CGImageSourceCreateWithData(sourceData as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(
                  source,
                  0,
                  [
                      kCGImageSourceCreateThumbnailFromImageAlways: true,
                      kCGImageSourceCreateThumbnailWithTransform: true,
                      kCGImageSourceThumbnailMaxPixelSize: Constants.initialMaxLongEdge,
                  ] as CFDictionary
              )
        else {
            throw PhotoUploadImageEncodingError.invalidSource
        }

        let normalizedImage = UIImage(cgImage: image, scale: 1, orientation: .up)
        var maxPixelSize = Constants.initialMaxLongEdge

        for round in 0...Constants.maxRescaleRounds {
            for quality in Constants.qualityLadder {
                guard let encoded = SDImageWebPCoder.shared.encodedData(
                    with: normalizedImage,
                    format: .webP,
                    options: [
                        .encodeCompressionQuality: quality,
                        .encodeMaxPixelSize: CGSize(
                            width: maxPixelSize,
                            height: maxPixelSize
                        ),
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
                maxPixelSize = max(
                    1,
                    Int(Double(maxPixelSize) * Constants.rescaleFactor)
                )
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
        static let rescaleFactor = 0.85
    }
}
