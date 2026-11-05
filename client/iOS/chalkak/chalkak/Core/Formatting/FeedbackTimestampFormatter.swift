import Foundation

/// 고객센터만 서울 시간대 기준으로 표시한다. 알림함의 기기 시간대 규칙은 바꾸지 않는다.
enum FeedbackTimestampFormatter {
    static func string(from date: Date, now: Date = .now) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = calendar.timeZone
        if calendar.isDate(date, inSameDayAs: now) {
            formatter.dateFormat = "HH:mm"
            return formatter.string(from: date)
        }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: now),
           calendar.isDate(date, inSameDayAs: yesterday) {
            formatter.dateFormat = "HH:mm"
            return "어제 \(formatter.string(from: date))"
        }
        formatter.dateFormat = "M월 d일 HH:mm"
        return formatter.string(from: date)
    }
}
