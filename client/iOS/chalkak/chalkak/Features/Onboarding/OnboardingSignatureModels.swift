import CoreGraphics
import Foundation

struct OnboardingSignaturePoint: Equatable, Sendable {
    let xRatio: CGFloat
    let yRatio: CGFloat

    init(xRatio: CGFloat, yRatio: CGFloat) {
        self.xRatio = xRatio.clamped(to: 0...1)
        self.yRatio = yRatio.clamped(to: 0...1)
    }
}

struct OnboardingSignatureStroke: Identifiable, Equatable, Sendable {
    let id = UUID()
    var points: [OnboardingSignaturePoint]

    init(points: [OnboardingSignaturePoint] = []) {
        self.points = points
    }

    var isEmpty: Bool {
        points.isEmpty
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
