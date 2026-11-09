import Foundation

/// 목록·상세에 쓰는 짧은 시각 표시. 기기 시간대 기준으로 오늘이면 `HH:mm`, 어제면 `어제 HH:mm`,
/// 그 밖에는 `M월 d일 HH:mm`이다. 예: 서버 `2026-11-04T01:00:00Z`는 한국 시간 같은 날이면 `10:00`.
enum ChalkakTimestampFormatter {
    static func string(from date: Date, calendar: Calendar = .current) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = calendar.timeZone

        if calendar.isDateInToday(date) {
            formatter.dateFormat = "HH:mm"
            return formatter.string(from: date)
        }
        if calendar.isDateInYesterday(date) {
            formatter.dateFormat = "HH:mm"
            return "어제 \(formatter.string(from: date))"
        }

        formatter.dateFormat = "M월 d일 HH:mm"
        return formatter.string(from: date)
    }
}
