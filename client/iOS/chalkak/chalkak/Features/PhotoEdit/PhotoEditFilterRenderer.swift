import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO
import UIKit

/// 미리보기·썸네일·완료 렌더링에 함께 적용하는 조정과 필터. 조정을 먼저 하고 그 위에 필터를 입힌다.
nonisolated struct PhotoEditLook: Equatable, Sendable {
    var filter: PhotoEditFilter = .original
    var adjustments = PhotoEditAdjustments()

    static let original = PhotoEditLook()

    var isOriginal: Bool {
        filter == .original && adjustments.isNeutral
    }
}

struct PhotoEditRenderer: Sendable {
    /// `maxLongEdge`가 있으면 긴 변을 그 크기로 줄여 렌더링하고, 없으면 원래 크기를 유지한다.
    typealias Render = @Sendable (UIImage, PhotoEditLook, CGFloat?) async -> UIImage?
    /// 반시계 방향 90° 회전 횟수와 좌우반전 여부를 적용한다.
    typealias Orient = @Sendable (UIImage, Int, Bool) async -> UIImage?
    /// 사진을 분석해 자동 보정값을 구한다.
    typealias Analyze = @Sendable (UIImage) async -> PhotoEditAdjustments?

    let render: Render
    let orient: Orient
    let analyze: Analyze

    init(
        render: @escaping Render,
        orient: @escaping Orient = { image, _, _ in image },
        analyze: @escaping Analyze = { _ in nil }
    ) {
        self.render = render
        self.orient = orient
        self.analyze = analyze
    }

    static let live = PhotoEditRenderer(
        render: { image, look, maxLongEdge in
            await Task.detached(priority: .userInitiated) {
                PhotoEditFilterRenderer.render(image, look: look, maxLongEdge: maxLongEdge)
            }.value
        },
        orient: { image, quarterTurns, isFlipped in
            await Task.detached(priority: .userInitiated) {
                PhotoEditFilterRenderer.orient(image, quarterTurns: quarterTurns, isFlipped: isFlipped)
            }.value
        },
        analyze: { image in
            await Task.detached(priority: .userInitiated) {
                PhotoEditAutoAdjuster.adjustments(for: image)
            }.value
        }
    )
}

enum PhotoEditFilterRenderer {
    // CIContext는 생성 비용이 크고 스레드 안전하므로 하나를 재사용한다.
    private nonisolated(unsafe) static let context = CIContext(options: [.cacheIntermediates: false])

    nonisolated static func render(
        _ image: UIImage,
        look: PhotoEditLook,
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
        } else if look.isOriginal, image.imageOrientation == .up {
            return image
        }

        let outputRect = CGRect(
            origin: input.extent.origin,
            size: CGSize(
                width: max(1, input.extent.width.rounded()),
                height: max(1, input.extent.height.rounded())
            )
        )
        let output = apply(look, to: input).cropped(to: outputRect)
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

    private nonisolated static func apply(_ look: PhotoEditLook, to image: CIImage) -> CIImage {
        apply(look.filter, to: apply(look.adjustments, to: image))
    }

    /// 노출 → 따뜻함 → 채도 → 톤 곡선 순서로 적용한다. 노출과 따뜻함은 선형 공간에서 곱한다.
    private nonisolated static func apply(_ adjustments: PhotoEditAdjustments, to image: CIImage) -> CIImage {
        var output = image

        if adjustments[.exposure] != 0 {
            let exposure = CIFilter.exposureAdjust()
            exposure.inputImage = output
            exposure.ev = Float(adjustments[.exposure] * PhotoEditToneCurve.exposureRangeEV)
            output = exposure.outputImage ?? output
        }

        if adjustments[.warmth] != 0 {
            let gain = adjustments[.warmth] * PhotoEditToneCurve.warmthGain
            let matrix = CIFilter.colorMatrix()
            matrix.inputImage = output
            matrix.rVector = CIVector(x: 1 + gain, y: 0, z: 0, w: 0)
            matrix.bVector = CIVector(x: 0, y: 0, z: 1 - gain, w: 0)
            output = matrix.outputImage ?? output
        }

        if adjustments[.saturation] != 0 {
            let colorControls = CIFilter.colorControls()
            colorControls.inputImage = output
            colorControls.saturation = Float(1 + adjustments[.saturation])
            output = colorControls.outputImage ?? output
        }

        if !PhotoEditToneCurve.isIdentity(adjustments) {
            let samples = PhotoEditToneCurve.samples(for: adjustments)
            let table = samples.flatMap { sample in Array(repeating: Float(sample), count: 3) }
            let curves = CIFilter.colorCurves()
            curves.inputImage = output
            curves.curvesData = table.withUnsafeBufferPointer { Data(buffer: $0) }
            curves.curvesDomain = CIVector(x: 0, y: 1)
            curves.colorSpace = Constants.sRGB
            output = curves.outputImage ?? output
        }
        return output
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
        nonisolated static let sRGB = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
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
