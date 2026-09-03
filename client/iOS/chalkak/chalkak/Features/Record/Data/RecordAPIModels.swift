import Foundation

struct RecordCalendarResponse: Decodable {
    let year: Int
    let month: Int
    let posts: [RecordCalendarItemResponse]
}

struct RecordCalendarItemResponse: Decodable {
    let topicDate: String
    let postId: String
    let thumbnailImageUrl: String
    let status: String
}

extension RecordCalendarResponse {
    /// Android `PostCalendarResponse.toDomain`의 검증 규칙을 그대로 옮긴다.
    /// - 응답 연월이 요청 연월과 일치해야 한다.
    /// - 각 항목의 날짜는 해당 월 안에 있어야 하고 postId/thumbnail 이 비어있지 않아야 한다.
    /// - 날짜 중복이 없어야 한다.
    func toDomain(requestedMonth: RecordMonth) throws -> RecordCalendar {
        guard month >= 1, month <= 12 else {
            throw RecordError.invalidResponse
        }
        let responseMonth = RecordMonth(year: year, month: month)
        guard responseMonth == requestedMonth else {
            throw RecordError.invalidResponse
        }

        let mapped = try posts.map { try $0.toDomain(month: responseMonth) }
        let uniqueDateCount = Set(mapped.map(\.topicDate)).count
        guard uniqueDateCount == mapped.count else {
            throw RecordError.invalidResponse
        }

        return RecordCalendar(
            month: responseMonth,
            posts: mapped.sorted { $0.topicDate < $1.topicDate }
        )
    }
}

extension RecordCalendarItemResponse {
    func toDomain(month: RecordMonth) throws -> RecordPost {
        guard let date = Self.dateFormatter.date(from: topicDate),
              month.contains(date),
              !postId.isEmpty,
              !thumbnailImageUrl.isEmpty,
              let thumbnailURL = URL(string: thumbnailImageUrl)
        else {
            throw RecordError.invalidResponse
        }

        return RecordPost(
            postId: postId,
            topicDate: date,
            thumbnailImageSource: .remote(thumbnailURL),
            status: RecordPostStatus(rawValue: status)
        )
    }

    static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
