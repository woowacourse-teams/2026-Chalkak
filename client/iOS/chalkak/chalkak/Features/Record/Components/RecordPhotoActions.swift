import SwiftUI

/// 선택한 사진에 대한 액션 버튼 행.
/// Android `RecordPhotoActions`와 맞춘다(Row spacing 20, 아웃라인 버튼 균등폭).
/// - `전시 보러가기`는 승인(APPROVED) 상태에서만 노출한다.
/// 화면 좌우 여백/상하 여백은 호출부가 관리한다.
struct RecordPhotoActions: View {
    @Environment(\.chalkakTheme) private var theme
    let onFeedClick: () -> Void
    let onDisplayClick: () -> Void
    var isDisplayVisible = true

    var body: some View {
        // Android `Arrangement.spacedBy(20.dp)` — 디자인 토큰에 20이 없어 상수로 맞춘다.
        HStack(spacing: Metrics.buttonSpacing) {
            ChalkakOutlinedButton(
                title: "피드에서 보기",
                action: onFeedClick,
                fillsWidth: true
            )
            if isDisplayVisible {
                ChalkakOutlinedButton(
                    title: "전시 보러가기",
                    action: onDisplayClick,
                    fillsWidth: true
                )
            }
        }
    }
}

private enum Metrics {
    static let buttonSpacing: CGFloat = 20
}

#Preview("Record Photo Actions", traits: .sizeThatFitsLayout) {
    VStack(spacing: 24) {
        RecordPhotoActions(onFeedClick: {}, onDisplayClick: {})
        RecordPhotoActions(onFeedClick: {}, onDisplayClick: {}, isDisplayVisible: false)
    }
    .padding(.horizontal, 20)
    .chalkakTheme(.light)
}
