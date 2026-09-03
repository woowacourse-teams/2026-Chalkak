import CoreGraphics
import UIKit

protocol OnboardingSignaturePngEncoder {
    func encode(_ strokes: [OnboardingSignatureStroke]) throws -> Data
}

struct DefaultOnboardingSignaturePngEncoder: OnboardingSignaturePngEncoder {
    func encode(_ strokes: [OnboardingSignatureStroke]) throws -> Data {
        let drawableStrokes = strokes.filter { !$0.isEmpty }
        guard !drawableStrokes.isEmpty else {
            throw OnboardingSignaturePngEncodingError.emptySignature
        }

        let points = drawableStrokes.flatMap(\.points)
        let minX = points.map(\.xRatio).min() ?? 0
        let maxX = points.map(\.xRatio).max() ?? 0
        let minY = points.map(\.yRatio).min() ?? 0
        let maxY = points.map(\.yRatio).max() ?? 0
        let contentWidth = max(maxX - minX, Constants.minimumNormalizedSize)
        let contentHeight = max(maxY - minY, Constants.minimumNormalizedSize)
        let availableWidth = Constants.outputWidth - Constants.padding * 2
        let availableHeight = Constants.outputHeight - Constants.padding * 2
        let scale = min(availableWidth / contentWidth, availableHeight / contentHeight)
        let offsetX = (Constants.outputWidth - contentWidth * scale) / 2
        let offsetY = (Constants.outputHeight - contentHeight * scale) / 2

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(
            size: CGSize(width: Constants.outputWidth, height: Constants.outputHeight),
            format: format
        )

        return renderer.pngData { rendererContext in
            let context = rendererContext.cgContext
            context.setStrokeColor(UIColor.white.cgColor)
            context.setFillColor(UIColor.white.cgColor)
            context.setLineWidth(Constants.strokeWidth)
            context.setLineCap(.round)
            context.setLineJoin(.round)

            for stroke in drawableStrokes {
                let mappedPoints = stroke.points.map { point in
                    CGPoint(
                        x: offsetX + (point.xRatio - minX) * scale,
                        y: offsetY + (point.yRatio - minY) * scale
                    )
                }
                guard let firstPoint = mappedPoints.first else { continue }

                if mappedPoints.count == 1 {
                    let radius = Constants.strokeWidth / 2
                    context.fillEllipse(
                        in: CGRect(
                            x: firstPoint.x - radius,
                            y: firstPoint.y - radius,
                            width: radius * 2,
                            height: radius * 2
                        )
                    )
                } else {
                    context.addPath(Self.smoothPath(for: mappedPoints))
                    context.strokePath()
                }
            }
        }
    }

    private static func smoothPath(for points: [CGPoint]) -> CGPath {
        let path = CGMutablePath()
        path.move(to: points[0])
        if points.count == 2 {
            path.addLine(to: points[1])
            return path
        }

        for index in 1..<(points.count - 1) {
            let current = points[index]
            let next = points[index + 1]
            path.addQuadCurve(
                to: CGPoint(
                    x: (current.x + next.x) / 2,
                    y: (current.y + next.y) / 2
                ),
                control: current
            )
        }
        path.addLine(to: points[points.count - 1])
        return path
    }

    private enum Constants {
        static let outputWidth: CGFloat = 1024
        static let outputHeight: CGFloat = 512
        static let padding: CGFloat = 48
        static let strokeWidth: CGFloat = 4
        static let minimumNormalizedSize: CGFloat = 0.02
    }
}

enum OnboardingSignaturePngEncodingError: Error {
    case emptySignature
}
