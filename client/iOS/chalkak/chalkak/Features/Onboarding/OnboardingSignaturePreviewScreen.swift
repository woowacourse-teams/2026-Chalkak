import SwiftUI

struct OnboardingSignaturePreviewScreen: View {
    @Environment(\.chalkakTheme) private var theme
    let strokes: [OnboardingSignatureStroke]
    let onRedraw: () -> Void
    let onStart: () -> Void
    var isSubmitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()
                .frame(height: Metrics.titleTopPadding)

            Text("이렇게 보여요")
                .font(theme.typography.title1)
                .foregroundStyle(theme.colors.textPrimary)

            Spacer()
                .frame(height: Metrics.imageTopPadding)

            SignedPreviewImage(strokes: strokes)
                .aspectRatio(Metrics.imageAspectRatio, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: theme.shapes.xlarge))

            Spacer(minLength: 0)

            HStack(spacing: Metrics.buttonSpacing) {
                ChalkakOutlinedButton(
                    title: "다시 그리기",
                    action: onRedraw,
                    isEnabled: !isSubmitting,
                    fillsWidth: true
                )

                ChalkakButton(
                    title: "시작하기",
                    action: onStart,
                    isEnabled: !isSubmitting,
                    fillsWidth: true
                )
            }

            Spacer()
                .frame(height: Metrics.bottomPadding)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private struct SignedPreviewImage: View {
    @Environment(\.chalkakTheme) private var theme
    let strokes: [OnboardingSignatureStroke]

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottomTrailing) {
                ChalkakImage(
                    source: .asset("preview_sunset"),
                    contentDescription: "사진에 사인이 적용된 모습",
                    contentMode: .fill
                )
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()

                SignatureArtwork(strokes: strokes)
                    .foregroundStyle(theme.colors.textOnImage)
                    .frame(
                        width: Metrics.signatureSize.width,
                        height: Metrics.signatureSize.height
                    )
                    .padding(theme.spacing.md)
                    .accessibilityHidden(true)
            }
        }
    }
}

private struct SignatureArtwork: View {
    let strokes: [OnboardingSignatureStroke]

    var body: some View {
        Canvas { context, size in
            for stroke in strokes where !stroke.points.isEmpty {
                if stroke.points.count == 1, let point = stroke.points.first {
                    context.fill(
                        Path(ellipseIn: CGRect(
                            x: point.cgPoint(in: size).x - Metrics.previewDotRadius,
                            y: point.cgPoint(in: size).y - Metrics.previewDotRadius,
                            width: Metrics.previewDotRadius * 2,
                            height: Metrics.previewDotRadius * 2
                        )),
                        with: .foreground
                    )
                } else {
                    context.stroke(
                        stroke.smoothPath(in: size),
                        with: .foreground,
                        style: StrokeStyle(
                            lineWidth: Metrics.previewStrokeWidth,
                            lineCap: .round,
                            lineJoin: .round
                        )
                    )
                }
            }
        }
    }
}

private enum Metrics {
    static let titleTopPadding: CGFloat = 50
    static let imageTopPadding: CGFloat = 50
    static let imageAspectRatio: CGFloat = 5 / 6
    static let buttonSpacing: CGFloat = 20
    static let bottomPadding: CGFloat = 18
    static let signatureSize = CGSize(width: 112, height: 84)
    static let previewStrokeWidth: CGFloat = 4
    static let previewDotRadius: CGFloat = 2
}

#Preview("Signature Preview") {
    OnboardingSignaturePreviewScreen(
        strokes: [
            OnboardingSignatureStroke(points: [
                OnboardingSignaturePoint(xRatio: 0.12, yRatio: 0.64),
                OnboardingSignaturePoint(xRatio: 0.24, yRatio: 0.52),
                OnboardingSignaturePoint(xRatio: 0.38, yRatio: 0.68),
                OnboardingSignaturePoint(xRatio: 0.55, yRatio: 0.48),
                OnboardingSignaturePoint(xRatio: 0.78, yRatio: 0.56)
            ])
        ],
        onRedraw: {},
        onStart: {}
    )
    .chalkakTheme(.light)
}
