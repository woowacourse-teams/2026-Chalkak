import CoreGraphics
import UIKit

struct SignaturePngEncoder {
    func encode(_ strokes: [SignatureStroke]) throws -> Data {
        let drawableStrokes = strokes.filter { !$0.isEmpty }
        guard !drawableStrokes.isEmpty else {
            throw SignaturePngEncodingError.emptySignature
        }

        let points = drawableStrokes.flatMap(\.points)
        let minX = points.map(\.xRatio).min() ?? 0
        let maxX = points.map(\.xRatio).max() ?? 0
        let minY = points.map(\.yRatio).min() ?? 0
        let maxY = points.map(\.yRatio).max() ?? 0
        let contentWidth = max(maxX - minX, Metrics.minimumNormalizedSize)
        let contentHeight = max(maxY - minY, Metrics.minimumNormalizedSize)
        let availableWidth = Metrics.outputWidth - Metrics.padding * 2
        let availableHeight = Metrics.outputHeight - Metrics.padding * 2
        let scale = min(availableWidth / contentWidth, availableHeight / contentHeight)
        let offsetX = (Metrics.outputWidth - contentWidth * scale) / 2
        let offsetY = (Metrics.outputHeight - contentHeight * scale) / 2

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(
            size: CGSize(width: Metrics.outputWidth, height: Metrics.outputHeight),
            format: format
        )

        return renderer.pngData { rendererContext in
            let context = rendererContext.cgContext
            context.setStrokeColor(UIColor.white.cgColor)
            context.setFillColor(UIColor.white.cgColor)
            context.setLineWidth(Metrics.strokeWidth)
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
                    let radius = Metrics.strokeWidth / 2
                    context.fillEllipse(
                        in: CGRect(
                            x: firstPoint.x - radius,
                            y: firstPoint.y - radius,
                            width: radius * 2,
                            height: radius * 2
                        )
                    )
                } else {
                    context.addPath(mappedPoints.smoothPath())
                    context.strokePath()
                }
            }
        }
    }
}

enum SignaturePngEncodingError: Error {
    case emptySignature
}

private extension Array where Element == CGPoint {
    func smoothPath() -> CGPath {
        let path = CGMutablePath()
        path.move(to: self[0])
        if count == 2 {
            path.addLine(to: self[1])
            return path
        }
        for index in 1..<(count - 1) {
            let current = self[index]
            let next = self[index + 1]
            path.addQuadCurve(
                to: CGPoint(x: (current.x + next.x) / 2, y: (current.y + next.y) / 2),
                control: current
            )
        }
        path.addLine(to: self[count - 1])
        return path
    }
}

private enum Metrics {
    static let outputWidth: CGFloat = 1024
    static let outputHeight: CGFloat = 512
    static let padding: CGFloat = 48
    static let strokeWidth: CGFloat = 14
    static let minimumNormalizedSize: CGFloat = 0.02
}

