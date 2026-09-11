import SwiftUI
import UIKit

struct SignatureChangeFlow: View {
    @Environment(\.dismiss) private var dismiss
    @State private var strokes: [SignatureStroke] = []
    @State private var signatureData: Data?
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    let onUpdate: (Data) async throws -> Void

    var body: some View {
        NavigationStack {
            Group {
                if let signatureData {
                    SignatureChangePreview(
                        signatureData: signatureData,
                        isSubmitting: isSubmitting,
                        errorMessage: errorMessage,
                        onRedraw: {
                            self.signatureData = nil
                            errorMessage = nil
                        },
                        onConfirm: updateSignature
                    )
                } else {
                    SignatureEditor(
                        strokes: $strokes,
                        onSubmit: showPreview
                    )
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("닫기", systemImage: "xmark", action: dismiss.callAsFunction)
                        .labelStyle(.iconOnly)
                        .disabled(isSubmitting)
                        .accessibilityLabel("닫기")
                }
            }
        }
        .interactiveDismissDisabled(isSubmitting)
    }

    private func showPreview() {
        do {
            signatureData = try SignaturePngEncoder().encode(strokes)
            errorMessage = nil
        } catch {
            errorMessage = "사인을 저장하지 못했어요. 다시 시도해 주세요."
        }
    }

    private func updateSignature() {
        guard let signatureData, !isSubmitting else { return }
        isSubmitting = true
        errorMessage = nil
        Task {
            do {
                try await onUpdate(signatureData)
                dismiss()
            } catch is CancellationError {
                isSubmitting = false
            } catch SettingsAPIError.unauthorized {
                isSubmitting = false
                dismiss()
            } catch let error as SettingsAPIError {
                isSubmitting = false
                errorMessage = error.signatureUpdateMessage
            } catch {
                isSubmitting = false
                errorMessage = "사인을 저장하지 못했어요. 다시 시도해 주세요."
            }
        }
    }
}

private struct SignatureEditor: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [SignatureStroke]
    let onSubmit: () -> Void

    private var hasSignature: Bool { strokes.contains { !$0.isEmpty } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("작가님의\n사인을 그려주세요")
                .font(theme.typography.title1)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.top, Metrics.titleTopPadding)

            Text("모든 사진에 함께할 사인이에요.\n자유롭게 남겨주시고, 실제 서명은 피해 주세요.")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .padding(.top, Metrics.descriptionTopPadding)

            SignaturePad(strokes: $strokes)
                .aspectRatio(1, contentMode: .fit)
                .padding(.top, Metrics.padTopPadding)

            HStack(spacing: Metrics.controlSpacing) {
                SignatureControlButton(
                    title: "되돌리기",
                    systemImage: "arrow.counterclockwise",
                    isEnabled: hasSignature,
                    action: { if hasSignature { strokes.removeLast() } }
                )
                SignatureControlButton(
                    title: "전체 지우기",
                    isEnabled: hasSignature,
                    action: { strokes.removeAll() }
                )
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Metrics.controlTopPadding)

            Spacer(minLength: theme.spacing.lg)

            ChalkakButton(
                title: "이 사인으로 할래요",
                action: onSubmit,
                isEnabled: hasSignature,
                fillsWidth: true
            )
                .frame(maxWidth: .infinity)
                .padding(.bottom, Metrics.bottomPadding)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private struct SignaturePad: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [SignatureStroke]

    var body: some View {
        VStack(spacing: 0) {
            SignatureCanvas(strokes: $strokes)
                .clipShape(RoundedRectangle(cornerRadius: theme.shapes.button))

            Text("여기에 손가락으로 그리기")
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.textMuted)
                .padding(.top, 12)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 14)
        .background(theme.colors.inputBackground)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.large)
                .stroke(theme.colors.textPrimary.opacity(0.12), lineWidth: 1)
        }
    }
}

private struct SignatureCanvas: View {
    @Environment(\.chalkakTheme) private var theme
    @Binding var strokes: [SignatureStroke]
    @State private var activeStrokeID: UUID?

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                theme.colors.actionPrimary
                Canvas { context, size in
                    context.stroke(
                        Path(
                            roundedRect: CGRect(origin: .zero, size: size).insetBy(dx: 0.5, dy: 0.5),
                            cornerRadius: 10
                        ),
                        with: .color(theme.colors.textOnImage.opacity(0.28)),
                        style: StrokeStyle(lineWidth: 1, dash: [6, 6])
                    )
                    for stroke in strokes where !stroke.isEmpty {
                        if stroke.points.count == 1, let point = stroke.points.first {
                            let center = point.point(in: size)
                            context.fill(
                                Path(ellipseIn: CGRect(x: center.x - 2, y: center.y - 2, width: 4, height: 4)),
                                with: .color(theme.colors.textOnImage)
                            )
                        } else {
                            context.stroke(
                                stroke.path(in: size),
                                with: .color(theme.colors.textOnImage),
                                style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
                            )
                        }
                    }
                }

                if strokes.allSatisfy(\.isEmpty) {
                    Text("사인")
                        .font(theme.typography.handwriting)
                        .foregroundStyle(theme.colors.textOnImage.opacity(0.56))
                        .scaleEffect(2.4)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { append($0.location, in: proxy.size) }
                    .onEnded { _ in
                        if strokes.last?.isEmpty == true { strokes.removeLast() }
                        activeStrokeID = nil
                    }
            )
            .accessibilityLabel("사인 입력창")
            .accessibilityDirectTouch()
        }
    }

    private func append(_ point: CGPoint, in size: CGSize) {
        let normalized = SignaturePoint(
            xRatio: point.x / max(size.width, 1),
            yRatio: point.y / max(size.height, 1)
        )
        if let activeStrokeID,
           let index = strokes.firstIndex(where: { $0.id == activeStrokeID }) {
            strokes[index].points.append(normalized)
        } else {
            let stroke = SignatureStroke(points: [normalized])
            strokes.append(stroke)
            activeStrokeID = stroke.id
        }
    }
}

private struct SignatureChangePreview: View {
    @Environment(\.chalkakTheme) private var theme
    let signatureData: Data
    let isSubmitting: Bool
    let errorMessage: String?
    let onRedraw: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("이렇게 보여요")
                .font(theme.typography.title1)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.top, Metrics.titleTopPadding)

            Text("사인 변경까지 시간이 조금 걸릴 수 있어요")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textMuted)
                .padding(.top, theme.spacing.sm)

            ZStack(alignment: .bottomTrailing) {
                ChalkakImage(
                    source: .asset("preview_photo"),
                    contentDescription: "사진에 사인이 적용된 모습"
                )
                if let image = UIImage(data: signatureData) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 112, height: 84)
                        .padding(.trailing, theme.spacing.sm)
                        .padding(.bottom, theme.spacing.sm)
                        .accessibilityHidden(true)
                }
            }
            .aspectRatio(5 / 6, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.xlarge))
            .padding(.top, 36)

            if let errorMessage {
                Text(errorMessage)
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.error)
                    .frame(maxWidth: .infinity)
                    .padding(.top, theme.spacing.md)
            }

            Spacer(minLength: theme.spacing.lg)

            HStack(spacing: 20) {
                ChalkakOutlinedButton(
                    title: "다시 그리기",
                    action: onRedraw,
                    isEnabled: !isSubmitting,
                    fillsWidth: true
                )
                .frame(maxWidth: .infinity)

                ChalkakButton(
                    title: "사인 변경하기",
                    action: onConfirm,
                    isEnabled: !isSubmitting,
                    fillsWidth: true
                )
                .frame(maxWidth: .infinity)
            }
            .padding(.bottom, Metrics.bottomPadding)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private extension SettingsAPIError {
    var signatureUpdateMessage: String {
        switch self {
        case .signatureTooLarge:
            "사인 이미지가 1MB를 초과했어요."
        case .network:
            "네트워크 연결을 확인해 주세요."
        case .http(400), .http(403):
            "사용할 수 없는 사인이에요. 다시 그려주세요."
        case .http(404):
            "사인 업로드를 찾을 수 없어요. 다시 시도해 주세요."
        default:
            "사인을 저장하지 못했어요. 다시 시도해 주세요."
        }
    }
}

private struct SignatureControlButton: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var systemImage: String?
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage {
                    Image(systemName: systemImage).accessibilityHidden(true)
                }
                Text(title).font(theme.typography.footnote)
            }
            .foregroundStyle(isEnabled ? theme.colors.textPrimary : theme.colors.textMuted)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .overlay { Capsule().stroke(theme.colors.border, lineWidth: 1) }
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private extension SignatureStroke {
    func path(in size: CGSize) -> Path {
        let points = points.map { $0.point(in: size) }
        var path = Path()
        guard let first = points.first else { return path }
        path.move(to: first)
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

private enum Metrics {
    static let titleTopPadding: CGFloat = 24
    static let descriptionTopPadding: CGFloat = 24
    static let padTopPadding: CGFloat = 28
    static let controlTopPadding: CGFloat = 24
    static let controlSpacing: CGFloat = 12
    static let bottomPadding: CGFloat = 18
}
