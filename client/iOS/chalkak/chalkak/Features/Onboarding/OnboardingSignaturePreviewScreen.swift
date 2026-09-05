import SwiftUI
import UIKit

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

                if let data = try? DefaultOnboardingSignaturePngEncoder().encode(strokes),
                   let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(
                            width: Metrics.signatureSize.width,
                            height: Metrics.signatureSize.height
                        )
                        .padding(.trailing, theme.spacing.sm)
                        .padding(.bottom, theme.spacing.sm)
                        .accessibilityHidden(true)
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
