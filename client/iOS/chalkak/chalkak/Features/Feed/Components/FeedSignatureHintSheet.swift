import SwiftUI

/// 사진의 사인을 누르면 작가 사진을 모아볼 수 있다고 알려 주는 시트 내용.
/// 살짝 어둡게 덮은 예시 사진 오른쪽 아래의 사인을 흰 손그림 동그라미로 짚고, 그 아래에 제목과 설명을 둔다. 버튼은 없다.
///
/// - `.sheet { FeedSignatureHintSheet() }`처럼 시트 안에 넣는다. 시트 높이·손잡이·배경은 이 뷰가 정한다.
/// - 닫기는 아래로 쓸어내리거나 바깥을 눌러서 한다. 언제 띄우고 닫혔을 때 무엇을 기록할지는 호출부가 정한다.
/// - 예시 사진은 온보딩 사인 미리보기와 같은 `preview_sunset`, 사인은 실제 회원 것이 아닌 예시 글자다.
struct FeedSignatureHintSheet: View {
    @Environment(\.chalkakTheme) private var theme
    var title = AuthorPostsSignatureHint.title
    var message = AuthorPostsSignatureHint.message
    // 설명 글이 실제로 차지하는 폭. 예시 사진의 양 끝을 설명 글 양 끝에 맞추는 데 쓴다. 재기 전에는 nil이다.
    @State private var messageWidth: CGFloat?

    var body: some View {
        let content = VStack(spacing: theme.spacing.lg) {
            artwork
                .frame(width: messageWidth)

            VStack(spacing: theme.spacing.sm) {
                Text(title)
                    .font(theme.typography.title3)
                    .foregroundStyle(theme.colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)

                Text(message)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    // 한 줄이면 글자 폭, 줄이 바뀌면 쓸 수 있는 폭 전체가 된다.
                    .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { messageWidth = $0 }
            }
            .multilineTextAlignment(.center)
        }
        .padding(.horizontal, theme.spacing.xl)
        .padding(.top, theme.spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .presentationDetents([.height(Metrics.sheetHeight)])
        .presentationDragIndicator(.visible)

        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .background(theme.colors.surfaceElevated)
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    // 가로로 긴 예시 사진. 사진 아래쪽(노을과 건물)을 살짝 어둡게 덮어 흰 사인과 동그라미가 잘 보이게 하고,
    // 피드처럼 오른쪽 아래에 사인을 둔다.
    private var artwork: some View {
        Color.clear
            .aspectRatio(Metrics.artworkAspectRatio, contentMode: .fit)
            .overlay(alignment: .bottom) {
                Image("preview_sunset")
                    .resizable()
                    .scaledToFill()
            }
            .overlay(theme.colors.scrim)
            .overlay(alignment: .bottomTrailing) {
                Text(Metrics.sampleSignature)
                    .font(theme.typography.handwriting)
                    .foregroundStyle(theme.colors.textOnImage)
                    .fixedSize()
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.sm)
                    .background {
                        FeedSignatureHintCircle()
                            .stroke(
                                theme.colors.textOnImage,
                                style: StrokeStyle(lineWidth: Metrics.circleLineWidth, lineCap: .round, lineJoin: .round)
                            )
                    }
                    // 손글씨 토큰은 20pt라 예시 사인으로는 작다. 온보딩 사인 입력칸처럼 글자와 동그라미를 함께 키운다.
                    .scaleEffect(Metrics.signatureScale, anchor: .bottomTrailing)
                    .padding(.trailing, theme.spacing.lg)
                    .padding(.bottom, theme.spacing.md)
            }
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
            .accessibilityElement()
            .accessibilityLabel("사진 오른쪽 아래의 사인을 동그라미로 표시한 예시")
    }
}

/// 손으로 한 번에 두른 듯한 동그라미. 끝이 시작점을 조금 지나쳐 겹치게 끝난다.
/// 68×56 격자 기준 좌표를 받은 크기에 맞춰 늘린다.
private struct FeedSignatureHintCircle: Shape {
    func path(in rect: CGRect) -> Path {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x / 68 * rect.width, y: rect.minY + y / 56 * rect.height)
        }
        var path = Path()
        path.move(to: point(36, 3))
        path.addCurve(to: point(4, 30), control1: point(14, 2), control2: point(2, 14))
        path.addCurve(to: point(44, 52), control1: point(6, 46), control2: point(26, 54))
        path.addCurve(to: point(64, 24), control1: point(62, 50), control2: point(66, 36))
        path.addCurve(to: point(30, 6), control1: point(62, 12), control2: point(50, 4))
        return path
    }
}

private enum Metrics {
    // 손잡이 아래 예시 사진, 제목, 설명 한 줄이 들어가고, 설명 아래로 시트 끝까지 40pt 남짓 남는 높이.
    static let sheetHeight: CGFloat = 233
    static let artworkAspectRatio: CGFloat = 2.35
    // 실제 회원 사인이 아닌 예시 사인. 온보딩 사인 입력칸의 "사인" 안내 글자와 같은 손글씨(handwriting)로 쓴다.
    static let sampleSignature = "사인"
    static let signatureScale: CGFloat = 1.5
    static let circleLineWidth: CGFloat = 2
}

#Preview("Signature Hint Sheet") {
    Color.gray
        .sheet(isPresented: .constant(true)) {
            FeedSignatureHintSheet()
        }
        .chalkakTheme(.light)
}
