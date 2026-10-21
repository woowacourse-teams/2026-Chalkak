import Foundation
import ImageIO
import UIKit

/// 큰 사진을 디코딩하기 전에 줄여 메모리 사용량을 제한한다.
enum PhotoImageDownsampler {
    nonisolated static let maxLongEdge = 4_096

    /// EXIF 방향을 반영해 긴 변이 `maxLongEdge` 이하인 `.up` 이미지로 디코딩한다.
    /// Android와 같은 결과를 내도록 2의 거듭제곱 배수로 줄인다.
    nonisolated static func downsample(data: Data, maxLongEdge: Int = maxLongEdge) -> UIImage? {
        guard maxLongEdge > 0,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0
        else {
            return nil
        }

        let longEdge = max(width, height)
        var sampleSize = 1
        while longEdge / sampleSize > maxLongEdge {
            sampleSize *= 2
        }
        let sampledLongEdge = max(1, Int(ceil(Double(longEdge) / Double(sampleSize))))
        guard let image = CGImageSourceCreateThumbnailAtIndex(
            source,
            0,
            [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: min(sampledLongEdge, maxLongEdge),
            ] as CFDictionary
        )
        else {
            return nil
        }

        return UIImage(cgImage: image, scale: 1, orientation: .up)
    }

    /// 무거운 디코딩을 메인 스레드 밖에서 실행한다.
    static func downsampleInBackground(data: Data, maxLongEdge: Int = maxLongEdge) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            downsample(data: data, maxLongEdge: maxLongEdge)
        }.value
    }
}
