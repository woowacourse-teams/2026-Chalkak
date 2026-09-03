import Foundation

struct RecordAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = RecordAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct RecordAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let configuration: RecordAPIConfiguration
    private let session: URLSession
    private let accessTokenProvider: AccessTokenProvider
    private let decoder: JSONDecoder

    init(
        configuration: RecordAPIConfiguration = .development,
        session: URLSession = .shared,
        accessTokenProvider: @escaping AccessTokenProvider = { nil }
    ) {
        self.configuration = configuration
        self.session = session
        self.accessTokenProvider = accessTokenProvider
        self.decoder = JSONDecoder()
    }

    func fetchCalendar(month: RecordMonth) async throws -> RecordCalendar {
        let response: RecordCalendarResponse = try await request(
            path: "posts/calendar",
            queryItems: [
                URLQueryItem(name: "year", value: String(month.year)),
                URLQueryItem(name: "month", value: String(month.month))
            ]
        )
        return try response.toDomain(requestedMonth: month)
    }

    private func request<Response: Decodable>(
        path: String,
        queryItems: [URLQueryItem]
    ) async throws -> Response {
        guard var components = URLComponents(
            url: configuration.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw RecordError.invalidResponse
        }
        components.queryItems = queryItems
        guard let url = components.url, url.scheme == "https" else {
            throw RecordError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = await accessTokenProvider(), !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw RecordError.invalidResponse
            }
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw Self.error(for: httpResponse.statusCode)
            }
            guard !data.isEmpty else {
                throw RecordError.invalidResponse
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw RecordError.invalidResponse
            }
        } catch let error as RecordError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw RecordError.network
        }
    }

    /// Android `HomeFailure.toRecordMessage()`의 상태 코드 분기와 맞춘다.
    private static func error(for statusCode: Int) -> RecordError {
        switch statusCode {
        case 401:
            .unauthorized
        case 400:
            .invalidMonth
        default:
            .generic
        }
    }
}
