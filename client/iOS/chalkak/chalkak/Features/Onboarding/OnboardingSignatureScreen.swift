import SwiftUI

struct OnboardingSignatureScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [OnboardingSignatureStroke]

    let onSubmit: () -> Void

    private var hasSignature: Bool {
        strokes.contains { !$0.isEmpty }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()
                .frame(height: Metrics.titleTopPadding)

            Text("작가님의\n사인을 그려주세요")
                .font(theme.typography.title1)
                .foregroundStyle(theme.colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()
                .frame(height: Metrics.descriptionTopPadding)

            Text("모든 사진에 함께할 사인이에요.\n자유롭게 남겨주시고, 실제 서명은 피해 주세요.")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()
                .frame(height: Metrics.padTopPadding)

            SignaturePad(strokes: $strokes)
                .aspectRatio(1, contentMode: .fit)

            Spacer()
                .frame(height: Metrics.controlTopPadding)

            HStack(spacing: Metrics.controlSpacing) {
                SignatureControlButton(
                    title: "되돌리기",
                    systemImageName: "arrow.counterclockwise",
                    action: undo
                )

                SignatureControlButton(
                    title: "전체 지우기",
                    action: { strokes.removeAll() }
                )
            }
            .frame(maxWidth: .infinity)

            Spacer(minLength: 0)

            ChalkakButton(
                title: "이 사인으로 할래요",
                action: {
                    guard hasSignature else { return }
                    onSubmit()
                },
                fillsWidth: true
            )

            Spacer()
                .frame(height: Metrics.bottomPadding)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }

    private func undo() {
        guard hasSignature else { return }
        strokes.removeLast()
    }
}

private struct SignaturePad: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [OnboardingSignatureStroke]

    var body: some View {
        VStack(spacing: 0) {
            SignatureCanvas(strokes: $strokes)
                .clipShape(RoundedRectangle(cornerRadius: theme.shapes.button))

            Text("여기에 손가락으로 그리기")
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.textMuted)
                .padding(.top, Metrics.padCaptionTopPadding)
        }
        .padding(.leading, Metrics.padOuterPadding)
        .padding(.top, Metrics.padOuterPadding)
        .padding(.trailing, Metrics.padOuterPadding)
        .padding(.bottom, Metrics.padBottomPadding)
        .background(theme.colors.inputBackground)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.large)
                .stroke(theme.colors.textPrimary.opacity(0.12), lineWidth: Metrics.borderWidth)
        }
    }
}

private struct SignatureCanvas: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [OnboardingSignatureStroke]
    @State private var activeStrokeID: UUID?

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                theme.colors.actionPrimary

                Canvas { context, size in
                    let rect = CGRect(origin: .zero, size: size)
                    let dashedBorder = Path(
                        roundedRect: rect.insetBy(dx: Metrics.canvasBorderInset, dy: Metrics.canvasBorderInset),
                        cornerRadius: Metrics.canvasCornerRadius
                    )
                    context.stroke(
                        dashedBorder,
                        with: .color(theme.colors.textOnImage.opacity(0.28)),
                        style: StrokeStyle(
                            lineWidth: Metrics.borderWidth,
                            dash: [Metrics.dashLength, Metrics.dashLength]
                        )
                    )

                    for stroke in strokes where !stroke.points.isEmpty {
                        if stroke.points.count == 1, let point = stroke.points.first {
                            context.fill(
                                Path(ellipseIn: CGRect(
                                    x: point.cgPoint(in: size).x - Metrics.dotRadius,
                                    y: point.cgPoint(in: size).y - Metrics.dotRadius,
                                    width: Metrics.dotRadius * 2,
                                    height: Metrics.dotRadius * 2
                                )),
                                with: .color(theme.colors.textOnImage)
                            )
                        } else {
                            context.stroke(
                                stroke.smoothPath(in: size),
                                with: .color(theme.colors.textOnImage),
                                style: StrokeStyle(
                                    lineWidth: Metrics.strokeWidth,
                                    lineCap: .round,
                                    lineJoin: .round
                                )
                            )
                        }
                    }
                }

                if strokes.allSatisfy(\.isEmpty) {
                    Text("사인")
                        .font(theme.typography.handwriting)
                        .foregroundStyle(theme.colors.textOnImage.opacity(0.56))
                        .scaleEffect(Metrics.placeholderScale)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        append(point: value.location.signaturePoint(in: proxy.size))
                    }
                    .onEnded { _ in
                        trimEmptyTrailingStroke()
                        activeStrokeID = nil
                    }
            )
            .accessibilityLabel("사인 입력창")
            .accessibilityDirectTouch()
        }
    }

    private func append(point: OnboardingSignaturePoint) {
        if let activeStrokeID,
           let index = strokes.firstIndex(where: { $0.id == activeStrokeID }) {
            strokes[index].points.append(point)
            return
        }

        let stroke = OnboardingSignatureStroke(points: [point])
        strokes.append(stroke)
        activeStrokeID = stroke.id
    }

    private func trimEmptyTrailingStroke() {
        if strokes.last?.isEmpty == true {
            strokes.removeLast()
        }
    }
}

private struct SignatureControlButton: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var systemImageName: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Metrics.controlIconSpacing) {
                if let systemImageName {
                    Image(systemName: systemImageName)
                        .font(.system(size: Metrics.controlIconSize, weight: .regular))
                        .accessibilityHidden(true)
                }

                Text(title)
                    .font(theme.typography.footnote)
            }
            .foregroundStyle(theme.colors.textPrimary)
            .padding(.horizontal, Metrics.controlHorizontalPadding)
            .padding(.vertical, Metrics.controlVerticalPadding)
            .background(theme.colors.surfaceElevated.opacity(0.001))
            .overlay {
                Capsule()
                    .stroke(theme.colors.border, lineWidth: Metrics.borderWidth)
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

private extension CGPoint {
    func signaturePoint(in size: CGSize) -> OnboardingSignaturePoint {
        OnboardingSignaturePoint(
            xRatio: (x / max(size.width, 1)).clamped(to: 0...1),
            yRatio: (y / max(size.height, 1)).clamped(to: 0...1)
        )
    }
}

extension OnboardingSignaturePoint {
    func cgPoint(in size: CGSize) -> CGPoint {
        CGPoint(x: xRatio * size.width, y: yRatio * size.height)
    }
}

extension OnboardingSignatureStroke {
    func smoothPath(in size: CGSize) -> Path {
        let points = points.map { $0.cgPoint(in: size) }
        var path = Path()
        guard let first = points.first else { return path }

        path.move(to: first)
        guard points.count > 1 else { return path }

        if points.count == 2 {
            path.addLine(to: points[1])
            return path
        }

        for index in 1..<(points.count - 1) {
            let current = points[index]
            let next = points[index + 1]
            path.addQuadCurve(
                to: CGPoint(x: (current.x + next.x) / 2, y: (current.y + next.y) / 2),
                control: current
            )
        }
        path.addLine(to: points[points.count - 1])
        return path
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

private enum Metrics {
    static let titleTopPadding: CGFloat = 50
    static let descriptionTopPadding: CGFloat = 30
    static let padTopPadding: CGFloat = 32
    static let controlTopPadding: CGFloat = 24
    static let controlSpacing: CGFloat = 12
    static let bottomPadding: CGFloat = 18
    static let padOuterPadding: CGFloat = 16
    static let padBottomPadding: CGFloat = 14
    static let padCaptionTopPadding: CGFloat = 12
    static let canvasBorderInset: CGFloat = 1
    static let canvasCornerRadius: CGFloat = 10
    static let dashLength: CGFloat = 6
    static let dotRadius: CGFloat = 2
    static let strokeWidth: CGFloat = 4
    static let borderWidth: CGFloat = 1
    static let placeholderScale: CGFloat = 2.4
    static let controlIconSpacing: CGFloat = 4
    static let controlIconSize: CGFloat = 15
    static let controlHorizontalPadding: CGFloat = 16
    static let controlVerticalPadding: CGFloat = 10
}

#Preview("Signature") {
    @Previewable @State var strokes: [OnboardingSignatureStroke] = []

    OnboardingSignatureScreen(
        strokes: $strokes,
        onSubmit: {}
    )
    .chalkakTheme(.light)
}
