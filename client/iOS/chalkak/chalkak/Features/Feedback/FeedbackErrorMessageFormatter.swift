import Foundation

/// 시각 전용 필드가 없는 동안 서버 문구의 UTC 시각만 표시용으로 변환한다.
/// 접수 제한·재전송 정책은 판정하지 않으며, 모르는 형식은 원문으로 남긴다.
enum FeedbackErrorMessageFormatter {
    static func string(from message: String) -> String {
        let pattern = #"(?:\(UTC\)(\s*:\s*))?(?<![A-Za-z0-9])([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?Z)(?![A-Za-z0-9])"#
        guard let expression = try? NSRegularExpression(pattern: pattern) else { return message }
        let source = message as NSString
        let matches = expression.matches(in: message, range: NSRange(location: 0, length: source.length))
        guard !matches.isEmpty else { return message }

        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime]
        let utc = DateFormatter()
        utc.locale = Locale(identifier: "en_US_POSIX")
        utc.calendar = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)
        utc.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"

        let display = DateFormatter()
        display.locale = Locale(identifier: "ko_KR")
        display.calendar = Calendar(identifier: .gregorian)
        display.timeZone = TimeZone(identifier: "Asia/Seoul")
        display.dateFormat = "M월 d일 a h:mm"

        let result = NSMutableString(string: message)
        for match in matches.reversed() {
            let timestamp = source.substring(with: match.range(at: 2))
            let wholeSeconds = String(timestamp.prefix(19))
            // ISO 파서가 보정하는 2월 30일 같은 날짜도 원문으로 남긴다.
            guard let date = parser.date(from: wholeSeconds + "Z"),
                  utc.string(from: date) == wholeSeconds,
                  let seconds = Int(wholeSeconds.suffix(2)), seconds < 60 else { continue }
            // Date의 정밀도보다 작은 소수 초도 버리지 않는다. 일찍 재시도하지 않게 분 단위로 올린다.
            let hasFractionalSeconds = timestamp.dropFirst(20).dropLast().contains { $0 != "0" }
            let displayDate = seconds > 0 || hasFractionalSeconds
                ? date.addingTimeInterval(TimeInterval(60 - seconds)) : date
            let prefix = match.range(at: 1).location == NSNotFound
                ? "" : source.substring(with: match.range(at: 1))
            result.replaceCharacters(in: match.range, with: prefix + display.string(from: displayDate))
        }
        return result as String
    }
}
