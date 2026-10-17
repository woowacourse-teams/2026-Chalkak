import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UIKit

struct PhotoEditRenderer: Sendable {
    /// `maxLongEdge`가 있으면 긴 변을 그 크기로 줄여 렌더링하고, 없으면 원래 크기를 유지한다.
    typealias Render = @Sendable (UIImage, PhotoEditFilter, CGFloat?) async -> UIImage?
    /// 반시계 방향 90° 회전 횟수와 좌우반전 여부를 적용한다.
    typealias Orient = @Sendable (UIImage, Int, Bool) async -> UIImage?

    let render: Render
    let orient: Orient

    init(render: @escaping Render, orient: @escaping Orient = { image, _, _ in image }) {
        self.render = render
        self.orient = orient
    }

    static let live = PhotoEditRenderer(
        render: { image, filter, maxLongEdge in
            await Task.detached(priority: .userInitiated) {
                PhotoEditFilterRenderer.render(image, filter: filter, maxLongEdge: maxLongEdge)
            }.value
        },
        orient: { image, quarterTurns, isFlipped in
            await Task.detached(priority: .userInitiated) {
                PhotoEditFilterRenderer.orient(image, quarterTurns: quarterTurns, isFlipped: isFlipped)
            }.value
        }
    )
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

    /// 반시계 방향으로 `quarterTurns`번 90° 돌린 뒤 좌우반전한다.
    nonisolated static func orient(_ image: UIImage, quarterTurns: Int, isFlipped: Bool) -> UIImage? {
        let turns = ((quarterTurns % 4) + 4) % 4
        guard turns != 0 || isFlipped || image.imageOrientation != .up else { return image }
        guard var input = ciImage(from: image) else { return nil }

        // Core Image 좌표계는 y축이 위를 향하므로 양의 각도가 반시계 방향이다.
        input = input.transformed(by: CGAffineTransform(rotationAngle: CGFloat(turns) * .pi / 2))
        if isFlipped {
            input = input.transformed(by: CGAffineTransform(scaleX: -1, y: 1))
        }
        input = input.transformed(by: CGAffineTransform(translationX: -input.extent.minX, y: -input.extent.minY))

        let outputRect = CGRect(
            origin: .zero,
            size: CGSize(width: input.extent.width.rounded(), height: input.extent.height.rounded())
        )
        guard let cgImage = context.createCGImage(input, from: outputRect) else { return nil }
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
