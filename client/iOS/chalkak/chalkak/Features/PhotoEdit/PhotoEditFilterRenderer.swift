import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UIKit

struct PhotoEditRenderer: Sendable {
    /// `maxLongEdge`가 있으면 긴 변을 그 크기로 줄여 렌더링하고, 없으면 원래 크기를 유지한다.
    typealias Render = @Sendable (UIImage, PhotoEditFilter, CGFloat?) async -> UIImage?

    let render: Render

    init(render: @escaping Render) {
        self.render = render
    }

    static let live = PhotoEditRenderer { image, filter, maxLongEdge in
        await Task.detached(priority: .userInitiated) {
            PhotoEditFilterRenderer.render(image, filter: filter, maxLongEdge: maxLongEdge)
        }.value
    }
}

enum PhotoEditFilterRenderer {
    // CIContext는 생성 비용이 크고 스레드 안전하므로 하나를 재사용한다.
    private nonisolated(unsafe) static let context = CIContext(options: [.cacheIntermediates: false])

    nonisolated static func render(
        _ image: UIImage,
        filter: PhotoEditFilter,
        maxLongEdge: CGFloat? = nil
    ) -> UIImage? {
        guard var input = ciImage(from: image) else { return nil }

        let longEdge = max(input.extent.width, input.extent.height)
        if let maxLongEdge, maxLongEdge > 0, longEdge > maxLongEdge {
            let scale = maxLongEdge / longEdge
            input = input.transformed(
                by: CGAffineTransform(scaleX: scale, y: scale),
                highQualityDownsample: true
            )
        } else if filter == .original, image.imageOrientation == .up {
            return image
        }

        let outputRect = CGRect(
            origin: input.extent.origin,
            size: CGSize(
                width: max(1, input.extent.width.rounded()),
                height: max(1, input.extent.height.rounded())
            )
        )
        let output = apply(filter, to: input).cropped(to: outputRect)
        guard let cgImage = context.createCGImage(output, from: outputRect) else { return nil }
        return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
    }

    private nonisolated static func ciImage(from image: UIImage) -> CIImage? {
        let source: CIImage
        if let cgImage = image.cgImage {
            source = CIImage(cgImage: cgImage)
        } else if let ciImage = image.ciImage {
            source = ciImage
        } else {
            return nil
        }
        let oriented = source.oriented(CGImagePropertyOrientation(image.imageOrientation))
        return oriented.transformed(
            by: CGAffineTransform(translationX: -oriented.extent.minX, y: -oriented.extent.minY)
        )
    }

    private nonisolated static func apply(_ filter: PhotoEditFilter, to image: CIImage) -> CIImage {
        switch filter {
        case .original:
            return image
        case .vivid:
            let colorControls = CIFilter.colorControls()
            colorControls.inputImage = image
            colorControls.saturation = Constants.vividSaturation
            colorControls.contrast = Constants.vividContrast
            return colorControls.outputImage ?? image
        case .chrome:
            let effect = CIFilter.photoEffectChrome()
            effect.inputImage = image
            return effect.outputImage ?? image
        case .fade:
            let effect = CIFilter.photoEffectFade()
            effect.inputImage = image
            return effect.outputImage ?? image
        case .mono:
            let effect = CIFilter.photoEffectMono()
            effect.inputImage = image
            return effect.outputImage ?? image
        case .noir:
            let effect = CIFilter.photoEffectNoir()
            effect.inputImage = image
            return effect.outputImage ?? image
        }
    }

    private enum Constants {
        nonisolated static let vividSaturation: Float = 1.25
        nonisolated static let vividContrast: Float = 1.08
    }
}

private extension CGImagePropertyOrientation {
    nonisolated init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}
