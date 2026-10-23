import SwiftUI

/// 기록 화면의 년/월 휠 시트. 기록이 있는 달(`availableMonths`)만 고를 수 있다.
/// 완료를 누르면 고른 달을 `onConfirm`으로 넘긴다. 같은 달인지 거르는 일은 ViewModel이 한다.
struct RecordMonthPickerSheet: View {
    let availableMonths: [RecordMonth]
    let onConfirm: (RecordMonth) -> Void
    @State private var pendingMonth: RecordMonth

    init(month: RecordMonth, availableMonths: [RecordMonth], onConfirm: @escaping (RecordMonth) -> Void) {
        // 현재 달이 목록에 없으면(월 목록 조회 실패 등) 휠이 비지 않도록 함께 보여준다.
        self.availableMonths = Array(Set(availableMonths + [month])).sorted()
        self.onConfirm = onConfirm
        _pendingMonth = State(initialValue: month)
    }

    var body: some View {
        let years = yearOptions
        let months = availableMonths.filter { $0.year == pendingMonth.year }
        ChalkakPickerSheet(title: "날짜 선택", onConfirm: { onConfirm(pendingMonth) }) {
            ChalkakWheelPicker(columns: [
                ChalkakWheelColumn(
                    accessibilityLabel: "연도",
                    titles: years.map { "\($0)년" },
                    selectedIndex: years.firstIndex(of: pendingMonth.year) ?? 0,
                    onSelect: { index in
                        pendingMonth = Self.closestMonth(
                            in: availableMonths,
                            year: years[index],
                            preferredMonth: pendingMonth.month
                        )
                    }
                ),
                ChalkakWheelColumn(
                    accessibilityLabel: "월",
                    titles: months.map { "\($0.month)월" },
                    selectedIndex: months.firstIndex(of: pendingMonth) ?? 0,
                    onSelect: { index in pendingMonth = months[index] }
                ),
            ])
        }
    }

    private var yearOptions: [Int] {
        Array(Set(availableMonths.map(\.year))).sorted()
    }

    /// `year`의 고를 수 있는 달 중 `preferredMonth`와 가장 가까운 달.
    /// 예: 2026년에 9·10월만 있으면 `preferredMonth = 3` → 2026년 9월.
    static func closestMonth(in months: [RecordMonth], year: Int, preferredMonth: Int) -> RecordMonth {
        months
            .filter { $0.year == year }
            .min { abs($0.month - preferredMonth) < abs($1.month - preferredMonth) }
            ?? RecordMonth(year: year, month: preferredMonth)
    }
}
