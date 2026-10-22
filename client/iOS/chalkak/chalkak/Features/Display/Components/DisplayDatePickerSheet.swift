import SwiftUI

/// 전시 화면의 년/월/일 휠 시트. `startDate`(첫 주제 날짜)부터 `endDate`(오늘)까지만 고를 수 있다.
///
/// 년·월을 바꿨을 때 일이 범위를 벗어나면 가장 가까운 날로 맞춘다(예: 10월 22일에서 9월로 → 9월 22일).
/// 완료를 누르면 고른 날짜를 `onConfirm`으로 넘긴다.
struct DisplayDatePickerSheet: View {
    let startDate: Date
    let endDate: Date
    let onConfirm: (Date) -> Void
    @State private var pending: DisplayPickerDay

    init(selectedDate: Date, startDate: Date, endDate: Date, onConfirm: @escaping (Date) -> Void) {
        self.startDate = startDate
        self.endDate = endDate
        self.onConfirm = onConfirm
        let selected = DisplayPickerDay(selectedDate)
        _pending = State(initialValue: Self.clamp(selected, start: DisplayPickerDay(startDate), end: DisplayPickerDay(endDate)))
    }

    var body: some View {
        let start = DisplayPickerDay(startDate)
        let end = DisplayPickerDay(endDate)
        ChalkakPickerSheet(title: "날짜 선택", onConfirm: { onConfirm(pending.date) }) {
            ChalkakWheelPicker(columns: [
                column(\.year, label: "연도", unit: "년", values: Array(start.year...end.year), start: start, end: end),
                column(\.month, label: "월", unit: "월", values: Self.months(year: pending.year, start: start, end: end), start: start, end: end),
                column(
                    \.day,
                    label: "일",
                    unit: "일",
                    values: Self.days(year: pending.year, month: pending.month, start: start, end: end),
                    start: start,
                    end: end
                ),
            ])
        }
    }

    // 한 열(년·월·일)을 고르면 나머지가 범위를 벗어나지 않도록 `clamp`로 맞춘다.
    private func column(
        _ keyPath: WritableKeyPath<DisplayPickerDay, Int>,
        label: String,
        unit: String,
        values: [Int],
        start: DisplayPickerDay,
        end: DisplayPickerDay
    ) -> ChalkakWheelColumn {
        ChalkakWheelColumn(
            accessibilityLabel: label,
            titles: values.map { "\($0)\(unit)" },
            selectedIndex: values.firstIndex(of: pending[keyPath: keyPath]) ?? 0,
            onSelect: { index in
                var next = pending
                next[keyPath: keyPath] = values[index]
                pending = Self.clamp(next, start: start, end: end)
            }
        )
    }

    /// `year`에서 고를 수 있는 월. 예: 시작 2026-09-17, 끝 2026-10-22 → [9, 10].
    static func months(year: Int, start: DisplayPickerDay, end: DisplayPickerDay) -> [Int] {
        let first = year == start.year ? start.month : 1
        let last = year == end.year ? end.month : 12
        return Array(first...last)
    }

    /// `year`년 `month`월에서 고를 수 있는 일. 예: 시작 2026-09-17이면 2026년 9월 → [17...30].
    static func days(year: Int, month: Int, start: DisplayPickerDay, end: DisplayPickerDay) -> [Int] {
        let isStartMonth = year == start.year && month == start.month
        let isEndMonth = year == end.year && month == end.month
        let first = isStartMonth ? start.day : 1
        let last = isEndMonth ? end.day : RecordMonth(year: year, month: month).lengthOfMonth
        return Array(first...last)
    }

    /// 년·월·일 조합을 실제로 고를 수 있는 날짜로 맞춘다.
    /// 그 달에 없는 날(예: 9월 31일)은 말일로, 범위 밖은 `start`·`end`로 당긴다.
    static func clamp(_ day: DisplayPickerDay, start: DisplayPickerDay, end: DisplayPickerDay) -> DisplayPickerDay {
        let length = RecordMonth(year: day.year, month: day.month).lengthOfMonth
        let valid = DisplayPickerDay(year: day.year, month: day.month, day: min(day.day, length))
        return min(max(valid, start), end)
    }
}

/// 휠에서 다루는 년·월·일(KST). `date`는 그날 0시(Asia/Seoul)다.
struct DisplayPickerDay: Comparable {
    var year: Int
    var month: Int
    var day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init(_ date: Date) {
        let components = RecordMonth.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: components.year!, month: components.month!, day: components.day!)
    }

    var date: Date {
        RecordMonth(year: year, month: month).date(day: day)
    }

    static func < (lhs: DisplayPickerDay, rhs: DisplayPickerDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }
}
