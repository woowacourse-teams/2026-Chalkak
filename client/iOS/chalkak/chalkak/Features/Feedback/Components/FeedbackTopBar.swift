import SwiftUI

/// 고객센터 목록·문의 상세 위의 제목 줄. 가운데 제목과 왼쪽 뒤로 가기 버튼만 있다.
struct FeedbackTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let title: String
    let onBackClick: () -> Void

    var body: some View {
        ZStack {
            Text(title)
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.horizontal, ChalkakNavigationButton.diameter)
                .accessibilityAddTraits(.isHeader)

            HStack(spacing: 0) {
                ChalkakNavigationButton(kind: .back, action: onBackClick)
                    .accessibilityIdentifier("feedback.back")

                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, Metrics.horizontalPadding)
        .frame(height: Metrics.height)
    }
}

private enum Metrics {
    // 알림함 제목 줄과 같은 높이·여백이라 화면을 오가도 제목 위치가 같다.
    static let height: CGFloat = 72
    static let horizontalPadding: CGFloat = 20
}

#Preview("Feedback Top Bar") {
    FeedbackTopBar(title: "고객센터", onBackClick: {})
        .chalkakTheme(.light)
}
