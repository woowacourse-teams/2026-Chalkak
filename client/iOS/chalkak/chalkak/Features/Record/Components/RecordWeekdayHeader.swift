import SwiftUI

/// 일~토 요일 헤더. Android `RecordWeekdayHeader`와 맞춘다(caption, textMuted, 균등폭 중앙정렬).
/// 화면 좌우 패딩은 호출부가 관리한다.
struct RecordWeekdayHeader: View {
    @Environment(\.chalkakTheme) private var theme

    private static let weekdays = ["일", "월", "화", "수", "목", "금", "토"]

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            ForEach(Self.weekdays, id: \.self) { weekday in
                Text(weekday)
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textMuted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview("Record Weekday Header", traits: .sizeThatFitsLayout) {
    RecordWeekdayHeader()
        .padding(.horizontal, 20)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
