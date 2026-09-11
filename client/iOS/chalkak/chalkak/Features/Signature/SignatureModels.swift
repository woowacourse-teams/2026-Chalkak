import CoreGraphics
import Foundation

struct SignaturePoint: Equatable, Sendable {
    let xRatio: CGFloat
    let yRatio: CGFloat

    init(xRatio: CGFloat, yRatio: CGFloat) {
        self.xRatio = xRatio.clamped(to: 0...1)
        self.yRatio = yRatio.clamped(to: 0...1)
    }

    func point(in size: CGSize) -> CGPoint {
        CGPoint(x: xRatio * size.width, y: yRatio * size.height)
    }
}

struct SignatureStroke: Identifiable, Equatable, Sendable {
    let id = UUID()
    var points: [SignaturePoint]

    init(points: [SignaturePoint] = []) {
        self.points = points
    }

    var isEmpty: Bool { points.isEmpty }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

