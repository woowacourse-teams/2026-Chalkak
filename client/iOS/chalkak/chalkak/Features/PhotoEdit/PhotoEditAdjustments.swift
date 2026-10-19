import CoreGraphics
import Foundation
import UIKit

/// 조정 화면에서 바꿀 수 있는 항목.
///
/// 값은 -1...1이고 0이면 바꾸지 않는다. Android와 같은 결과를 내도록 계산식은 `PhotoEditToneCurve`와
/// `PhotoEditFilterRenderer`에 플랫폼 API에 기대지 않는 형태로 둔다.
nonisolated enum PhotoEditAdjustment: CaseIterable, Identifiable, Sendable {
    case exposure
    case contrast
    case highlights
    case shadows
    case blackPoint
    case saturation
    case warmth

    var id: Self { self }

    var title: String {
        switch self {
        case .exposure: "노출"
        case .contrast: "대비"
        case .highlights: "하이라이트"
        case .shadows: "그림자"
        case .blackPoint: "블랙 포인트"
        case .saturation: "채도"
        case .warmth: "따뜻함"
        }
    }

    var systemImage: String {
        switch self {
        case .exposure: "plusminus.circle"
        case .contrast: "circle.lefthalf.filled"
        case .highlights: "sun.max"
        case .shadows: "circle.bottomhalf.filled"
        case .blackPoint: "circle.fill"
        case .saturation: "drop.halffull"
        case .warmth: "thermometer.medium"
        }
    }
}

/// 항목별 조정값. 0인 항목은 보관하지 않는다.
nonisolated struct PhotoEditAdjustments: Equatable, Sendable {
    private var values: [PhotoEditAdjustment: Double] = [:]

    init() {}

    init(_ values: [PhotoEditAdjustment: Double]) {
        for (adjustment, value) in values {
            self[adjustment] = value
        }
    }

    subscript(adjustment: PhotoEditAdjustment) -> Double {
        get { values[adjustment] ?? 0 }
        set { values[adjustment] = newValue == 0 ? nil : newValue }
    }

    var isNeutral: Bool {
        values.isEmpty
    }

    /// 항목마다 `overrides`에 값이 있으면 그 값을, 없으면 `self × scale`을 쓴다.
    func scaled(by scale: Double, overriddenBy overrides: [PhotoEditAdjustment: Double]) -> PhotoEditAdjustments {
        var result = PhotoEditAdjustments()
        for adjustment in PhotoEditAdjustment.allCases {
            result[adjustment] = overrides[adjustment] ?? self[adjustment] * scale
        }
        return result
    }
}

/// 자동 보정값을 정한다.
///
/// Apple 특허(US 8,983,186 B2)와 Core Image 자동 보정의 동작에서 가져온 원칙을 따른다.
/// - 히스토그램 백분위수로 검은 점·흰 점을 잡되, 끝까지 늘리지 않고 덜어 내서(tempering) 과보정을 피한다.
/// - 중간 밝기는 옮기지 않고, 중간 대비는 높이기만 하고 낮추지 않는다.
/// - 그림자 들어 올림은 0.45 정도에서 멈춘다.
/// - 채도가 이미 높은 사진은 건드리지 않고, 피부색 픽셀 비율만큼 덜 올려 인물 사진이 과하게 진해지지 않게 한다.
/// - 단계마다 앞 단계를 적용한 결과를 보고 다음 값을 정한다(노출 → 블랙 포인트 → 그림자·하이라이트 → 대비).
/// - 문턱 없이 사진 상태에 비례해 값을 정한다. 잘 찍힌 사진도 작은 값이 여러 항목에 채워진다.
nonisolated enum PhotoEditAutoAdjuster {
    static func adjustments(for image: UIImage) -> PhotoEditAdjustments? {
        guard let statistics = PhotoEditImageStatistics(image: image) else { return nil }
        return adjustments(for: statistics)
    }

    static func adjustments(for statistics: PhotoEditImageStatistics) -> PhotoEditAdjustments {
        var adjustments = PhotoEditAdjustments()
        // 히스토그램 위치를 앞 단계 보정을 적용한 위치로 옮긴다. 보정은 단조 증가라 백분위수 순서가 유지된다.
        var transform: (Double) -> Double = { $0 }
        func percentile(_ fraction: Double) -> Double {
            transform(statistics.luminancePercentile(fraction))
        }

        // 노출: 흰 쪽이 비어 있으면 흰 점이 목표에 가까워지도록 올리고, 전체가 밝으면 조금 내린다.
        let white = percentile(Constants.whitePercentile)
        var exposureEV = 0.0
        if white < Constants.targetWhite {
            let gain = PhotoEditToneCurve.linear(Constants.targetWhite) / PhotoEditToneCurve.linear(max(white, 0.05))
            exposureEV = log2(gain) * Constants.exposureTempering
        } else if percentile(0.5) > Constants.brightMedian {
            exposureEV = -(percentile(0.5) - Constants.brightMedian) * Constants.brightExposureSlope
        }
        exposureEV = min(max(exposureEV, Constants.minimumExposureEV), Constants.maximumExposureEV)
        adjustments[.exposure] = rounded(exposureEV / PhotoEditToneCurve.exposureRangeEV)
        let exposed = transform
        let exposureGain = pow(2, adjustments[.exposure] * PhotoEditToneCurve.exposureRangeEV)
        transform = { PhotoEditToneCurve.gamma(min(1, PhotoEditToneCurve.linear(exposed($0)) * exposureGain)) }

        // 블랙 포인트: 가장 어두운 쪽이 떠 있는 만큼 일부만 끌어내린다.
        let black = percentile(Constants.blackPercentile)
        let inputBlackTarget = min(max(black - Constants.blackMargin, 0) * Constants.blackTempering, Constants.maximumInputBlack)
        adjustments[.blackPoint] = rounded(inputBlackTarget / PhotoEditToneCurve.blackPointRange)
        let leveled = transform
        let inputBlack = max(adjustments[.blackPoint], 0) * PhotoEditToneCurve.blackPointRange
        transform = { max(0, leveled($0) - inputBlack) / (1 - inputBlack) }

        // 그림자·하이라이트: 어두운 영역·밝은 영역이 넓을수록 살린다. 좁은 영역에도 반응하도록 1보다 작은 지수로 올린다.
        let darkFraction = statistics.fraction { transform($0) < Constants.darkLuminance }
        adjustments[.shadows] = rounded(
            Constants.maximumShadowLift * response(darkFraction / Constants.fullShadowFraction)
        )
        let brightFraction = statistics.fraction { transform($0) > Constants.brightLuminance }
        adjustments[.highlights] = rounded(
            -Constants.maximumHighlightRecovery * response(brightFraction / Constants.fullHighlightFraction)
        )

        // 대비: 중간 50%가 좁게 몰려 있으면 넓힌다. 낮추지는 않는다.
        let midSpread = percentile(0.75) - percentile(0.25)
        adjustments[.contrast] = rounded(
            min(max((Constants.targetMidSpread - midSpread) * Constants.contrastSlope, 0), Constants.maximumContrast)
        )

        // 채도: 흑백에 가까운 사진은 그대로 두고, 덜 선명한 사진만 조금 올린다.
        // 피부는 조금만 진해져도 어색해 보이므로 피부색 픽셀 비율만큼 덜 올린다(사진 전체가 피부색이면 0).
        let saturation = statistics.saturation90
        if saturation > Constants.grayscaleSaturation {
            let value = min(max((Constants.targetSaturation - saturation) * Constants.saturationSlope, 0), Constants.maximumSaturation)
            adjustments[.saturation] = rounded(value * (1 - statistics.skinFraction))
        }
        return adjustments
    }

    private static func response(_ ratio: Double) -> Double {
        pow(min(max(ratio, 0), 1), Constants.responseExponent)
    }

    private static func rounded(_ value: Double) -> Double {
        (value * 100).rounded() / 100
    }

    private enum Constants {
        static let whitePercentile = 0.995
        static let blackPercentile = 0.005
        static let targetWhite = 0.96
        static let exposureTempering = 0.6
        static let brightMedian = 0.62
        static let brightExposureSlope = 1.2
        static let minimumExposureEV = -0.5
        static let maximumExposureEV = 1.0

        static let blackMargin = 0.01
        static let blackTempering = 0.8
        static let maximumInputBlack = 0.25

        static let responseExponent = 0.7
        static let darkLuminance = 0.25
        /// 어두운 영역이 이만큼이면 그림자를 최대로 들어 올린다.
        static let fullShadowFraction = 0.8
        static let maximumShadowLift = 0.45
        static let brightLuminance = 0.85
        /// 밝은 영역이 이만큼이면 하이라이트를 최대로 누른다.
        static let fullHighlightFraction = 0.3
        static let maximumHighlightRecovery = 0.5

        static let targetMidSpread = 0.42
        static let contrastSlope = 2.0
        static let maximumContrast = 0.4

        static let grayscaleSaturation = 0.05
        static let targetSaturation = 0.7
        static let saturationSlope = 0.6
        static let maximumSaturation = 0.25
    }
}

/// 자동 보정에 쓰는 밝기 히스토그램과 채도 통계. 긴 변 256px로 줄여서 센다.
nonisolated struct PhotoEditImageStatistics: Sendable {
    /// 0...255 밝기(sRGB 감마 공간의 Rec. 709 가중치)의 픽셀 비율.
    let luminanceHistogram: [Double]
    /// 채도(가장 큰 채널 − 가장 작은 채널, 저장 값 기준) 분포의 90번째 백분위수.
    let saturation90: Double
    /// 피부색으로 보이는 픽셀의 비율(0...1). 기준은 `isSkinTone` 참고.
    let skinFraction: Double

    init(luminanceHistogram: [Double], saturation90: Double, skinFraction: Double = 0) {
        self.luminanceHistogram = luminanceHistogram
        self.saturation90 = saturation90
        self.skinFraction = skinFraction
    }

    init?(image: UIImage) {
        guard let cgImage = image.cgImage,
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)
        else { return nil }
        let scale = min(1, Constants.sampleLongEdge / CGFloat(max(cgImage.width, cgImage.height)))
        let width = max(1, Int((CGFloat(cgImage.width) * scale).rounded()))
        let height = max(1, Int((CGFloat(cgImage.height) * scale).rounded()))
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
            ) else { return false }
            context.interpolationQuality = .medium
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }

        var histogram = [Double](repeating: 0, count: 256)
        var saturationHistogram = [Double](repeating: 0, count: 256)
        var skinCount = 0
        for index in stride(from: 0, to: pixels.count, by: 4) {
            let red = Double(pixels[index]) / 255
            let green = Double(pixels[index + 1]) / 255
            let blue = Double(pixels[index + 2]) / 255
            let luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            histogram[min(255, Int((luminance * 255).rounded()))] += 1

            let saturation = max(red, green, blue) - min(red, green, blue)
            saturationHistogram[min(255, Int((saturation * 255).rounded()))] += 1
            if Self.isSkinTone(red: red, green: green, blue: blue) {
                skinCount += 1
            }
        }
        let count = Double(width * height)
        luminanceHistogram = histogram.map { $0 / count }
        saturation90 = Self.percentile(0.9, in: saturationHistogram.map { $0 / count })
        skinFraction = Double(skinCount) / count
    }

    /// 피부색으로 볼지. 저장 값 R·G·B(0...1)가 모두 맞아야 한다.
    /// - 빨강 ≥ 초록 ≥ 파랑이고, 가장 큰 값(빨강)이 0.2 이상이다(너무 어두운 픽셀 제외).
    /// - (빨강 − 파랑) ÷ 빨강이 0.1...0.6이다. 회색에 가깝거나(0.1 미만) 주황처럼 쨍한 색(0.6 초과)은 뺀다.
    /// - 색상이 0...50°다. 빨강(0°)에서 노랑(60°) 사이 중 노랑에 가까운 쪽은 뺀다.
    static func isSkinTone(red: Double, green: Double, blue: Double) -> Bool {
        guard red >= green, green >= blue, red >= Constants.skinMinimumValue else { return false }
        let chroma = red - blue
        guard (Constants.skinMinimumChromaRatio...Constants.skinMaximumChromaRatio).contains(chroma / red) else {
            return false
        }
        return 60 * (green - blue) / chroma <= Constants.skinMaximumHueDegrees
    }

    func luminancePercentile(_ fraction: Double) -> Double {
        Self.percentile(fraction, in: luminanceHistogram)
    }

    /// 밝기가 조건을 만족하는 픽셀의 비율.
    func fraction(where predicate: (Double) -> Bool) -> Double {
        luminanceHistogram.enumerated().reduce(0) { sum, bin in
            predicate(Double(bin.offset) / 255) ? sum + bin.element : sum
        }
    }

    private static func percentile(_ fraction: Double, in histogram: [Double]) -> Double {
        var cumulative = 0.0
        for (index, value) in histogram.enumerated() {
            cumulative += value
            if cumulative >= fraction {
                return Double(index) / 255
            }
        }
        return 1
    }

    private enum Constants {
        static let sampleLongEdge: CGFloat = 256
        static let skinMinimumValue = 0.2
        static let skinMinimumChromaRatio = 0.1
        static let skinMaximumChromaRatio = 0.6
        static let skinMaximumHueDegrees = 50.0
    }
}

/// 블랙 포인트·그림자·하이라이트·대비를 하나의 톤 곡선으로 만든다. 곡선은 sRGB 감마 공간에서 채널마다 적용한다.
nonisolated enum PhotoEditToneCurve {
    /// 노출 1.0이 올리는 EV.
    static let exposureRangeEV = 1.5
    /// 블랙 포인트 1.0이 검게 만드는 입력 밝기. -1.0이면 같은 만큼 검은색을 띄운다.
    static let blackPointRange = 0.3
    /// 따뜻함 1.0이 빨강을 올리고 파랑을 내리는 비율(선형 공간).
    static let warmthGain = 0.25
    static let sampleCount = 256

    static func isIdentity(_ adjustments: PhotoEditAdjustments) -> Bool {
        [.blackPoint, .shadows, .highlights, .contrast].allSatisfy { adjustments[$0] == 0 }
    }

    /// 0...1 입력을 `sampleCount`개로 나눈 출력값. 어떤 조합에서도 단조 증가한다.
    static func samples(for adjustments: PhotoEditAdjustments) -> [Double] {
        let blackPoint = adjustments[.blackPoint]
        let inputBlack = max(blackPoint, 0) * blackPointRange
        let outputBlack = max(-blackPoint, 0) * blackPointRange
        let shadows = adjustments[.shadows]
        let highlights = adjustments[.highlights]
        let contrast = adjustments[.contrast]

        var previous = 0.0
        return (0..<sampleCount).map { index in
            let x = Double(index) / Double(sampleCount - 1)
            var t = min(max((x - inputBlack) / (1 - inputBlack), 0), 1)
            // 어두운 쪽(1/3 부근)과 밝은 쪽(2/3 부근)만 볼록하게 움직이고 0과 1은 그대로 둔다.
            t += shadows * Constants.toneAmount * 4 * t * (1 - t) * (1 - t)
            t += highlights * Constants.toneAmount * 4 * t * t * (1 - t)
            // 0.5를 축으로 하는 S자 곡선.
            t -= contrast * Constants.contrastAmount * sin(2 * .pi * t) / (2 * .pi)
            let y = outputBlack + (1 - outputBlack) * min(max(t, 0), 1)
            previous = max(previous, y)
            return previous
        }
    }

    static func linear(_ value: Double) -> Double {
        value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4)
    }

    static func gamma(_ value: Double) -> Double {
        value <= 0.0031308 ? value * 12.92 : 1.055 * pow(value, 1 / 2.4) - 0.055
    }

    private enum Constants {
        static let toneAmount = 0.25
        static let contrastAmount = 0.6
    }
}
