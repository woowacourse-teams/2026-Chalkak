import Foundation

/// 연월을 표현하는 값 타입. Java `YearMonth`(Android)에 대응한다.
/// 모든 날짜 계산은 Android와 동일하게 Asia/Seoul 그레고리력을 기준으로 한다.
struct RecordMonth: Equatable, Comparable, Sendable {
    let year: Int
    let month: Int

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        calendar.locale = Locale(identifier: "ko_KR")
        return calendar
    }

    static func current(now: Date = Date()) -> RecordMonth {
        let components = calendar.dateComponents([.year, .month], from: now)
        return RecordMonth(year: components.year!, month: components.month!)
    }

    /// 해당 월의 1일 자정(Asia/Seoul).
    private var firstDayComponents: DateComponents {
        DateComponents(year: year, month: month, day: 1)
    }

    private var firstDate: Date {
        Self.calendar.date(from: firstDayComponents)!
    }

    /// 해당 월의 일수.
    var lengthOfMonth: Int {
        Self.calendar.range(of: .day, in: .month, for: firstDate)!.count
    }

    /// 1일의 요일을 일요일=0 기준 인덱스로 반환한다.
    /// Android `atDay(1).dayOfWeek.value % 7`(월=1..일=7 → 일=0)과 결과를 맞춘다.
    var leadingEmptyDayCount: Int {
        // Calendar.weekday: 일요일 = 1 ... 토요일 = 7
        Self.calendar.component(.weekday, from: firstDate) - 1
    }

    func date(day: Int) -> Date {
        Self.calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    func adding(months: Int) -> RecordMonth {
        let base = firstDate
        let moved = Self.calendar.date(byAdding: .month, value: months, to: base)!
        let components = Self.calendar.dateComponents([.year, .month], from: moved)
        return RecordMonth(year: components.year!, month: components.month!)
    }

    func contains(_ date: Date) -> Bool {
        let components = Self.calendar.dateComponents([.year, .month], from: date)
        return components.year == year && components.month == month
    }

    var formatted: String {
        "\(year)년 \(month)월"
    }

    static func < (lhs: RecordMonth, rhs: RecordMonth) -> Bool {
        (lhs.year, lhs.month) < (rhs.year, rhs.month)
    }
}
