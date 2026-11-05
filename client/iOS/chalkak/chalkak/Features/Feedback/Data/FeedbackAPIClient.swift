import Foundation

struct FeedbackAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = FeedbackAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct FeedbackAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let configuration: FeedbackAPIConfiguration
    private let authenticatedClient: AuthenticatedHTTPClient
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(
        configuration: FeedbackAPIConfiguration = .development,
        session: URLSession = .shared,
        accessTokenProvider: @escaping AccessTokenProvider = { nil }
    ) {
        self.configuration = configuration
        self.authenticatedClient = AuthenticatedHTTPClient(
            baseURL: configuration.baseURL,
            session: session,
            sessionStore: .live(accessTokenProvider: accessTokenProvider)
        )
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    @discardableResult
    func submitFeedback(content: String) async throws -> FeedbackSubmissionResponse {
        let normalizedContent = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedContent.isEmpty,
              normalizedContent.unicodeScalars.count <= FeedbackLimits.maximumContentLength
        else {
            throw FeedbackAPIError.invalidContent
        }

        let body: Data
        do {
            body = try encoder.encode(FeedbackSubmissionRequest(content: normalizedContent))
        } catch {
            throw FeedbackAPIError.invalidResponse
        }

        var request = try makeRequest(path: "feedbacks", method: "POST")
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let data = try await send(request)
        guard !data.isEmpty else {
            throw FeedbackAPIError.invalidResponse
        }
        do {
            let response = try decoder.decode(FeedbackSubmissionResponse.self, from: data)
            guard !response.feedbackID.isEmpty, !response.createdAt.isEmpty else {
                throw FeedbackAPIError.invalidResponse
            }
            return response
        } catch {
            throw FeedbackAPIError.invalidResponse
        }
    }

    @discardableResult
    func submit(content: String) async throws -> FeedbackSubmissionResponse {
        try await submitFeedback(content: content)
    }

    /// 내 문의 목록 한 페이지. `page`는 1부터, 응답은 접수 시각 내림차순이다.
    func fetchFeedbacks(page: Int, pageSize: Int = 20) async throws -> FeedbackPage {
        let request = try makeRequest(path: "feedbacks", queryItems: [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize)),
        ])
        let data = try await send(request)
        do {
            return try decoder.decode(FeedbackPageResponse.self, from: data).page()
        } catch {
            throw FeedbackAPIError.invalidResponse
        }
    }

    /// 문의 한 건과 현재 답변. 다른 계정·삭제된 문의는 404 → `.notFound`.
    func fetchFeedback(id: String) async throws -> FeedbackDetail {
        let request = try makeRequest(path: "feedbacks/\(try Self.pathID(id))")
        let data = try await send(request)
        do {
            return try decoder.decode(FeedbackDetailResponse.self, from: data).detail()
        } catch {
            throw FeedbackAPIError.invalidResponse
        }
    }

    /// 성공은 본문 없는 204라 응답 본문을 읽지 않는다. 다른 계정·없는 문의는 404 → `.notFound`.
    func deleteFeedback(id: String) async throws {
        let request = try makeRequest(path: "feedbacks/\(try Self.pathID(id))", method: "DELETE")
        _ = try await send(request)
    }

    // API 경로에 그대로 들어가는 값이라 UUID 형식만 받는다. 형식이 틀리면 찾을 수 없는 문의로 본다.
    private static func pathID(_ id: String) throws -> String {
        guard UUID(uuidString: id) != nil else { throw FeedbackAPIError.notFound }
        return id
    }

    private func makeRequest(
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem] = []
    ) throws -> URLRequest {
        let url = configuration.baseURL.appendingPathComponent(path)
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            throw FeedbackAPIError.configuration
        }
        if !queryItems.isEmpty { components.queryItems = queryItems }
        guard let requestURL = components.url else { throw FeedbackAPIError.configuration }

        var request = URLRequest(url: requestURL)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    /// 2xx면 본문을 돌려주고, 그 밖의 상태와 전송 오류는 `FeedbackAPIError`로 바꾼다.
    private func send(_ request: URLRequest) async throws -> Data {
        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                throw apiError(statusCode: response.statusCode, data: data)
            }
            return data
        } catch let error as FeedbackAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw FeedbackAPIError.unauthorized
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw FeedbackAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw FeedbackAPIError.network
        }
    }

    private func apiError(statusCode: Int, data: Data) -> FeedbackAPIError {
        if statusCode == 401 {
            return .unauthorized
        }
        if statusCode == 404 {
            return .notFound
        }

        let errorResponse = try? decoder.decode(FeedbackErrorResponse.self, from: data)
        return .http(statusCode: statusCode, message: errorResponse?.message)
    }
}

enum FeedbackAPIError: Error, Equatable, Sendable {
    case configuration
    case network
    case invalidResponse
    case invalidContent
    case unauthorized
    /// 404. 상세·삭제에서 타인·삭제·없는 문의를 구분하지 않고 같은 값으로 온다.
    case notFound
    case http(statusCode: Int, message: String?)
}

struct FeedbackSubmissionResponse: Decodable, Equatable, Sendable {
    let feedbackID: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case feedbackID = "feedbackId"
        case createdAt
    }
}

private struct FeedbackPageResponse: Decodable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool
    let feedbacks: [FeedbackSummaryResponse]

    func page() throws -> FeedbackPage {
        FeedbackPage(
            currentPage: currentPage,
            pageSize: pageSize,
            hasNext: hasNext,
            items: try feedbacks.map { try $0.summary() }
        )
    }
}

private struct FeedbackSummaryResponse: Decodable {
    let feedbackId: String
    let content: String
    let createdAt: String

    func summary() throws -> FeedbackSummary {
        guard UUID(uuidString: feedbackId) != nil else { throw FeedbackAPIError.invalidResponse }
        return FeedbackSummary(
            id: feedbackId,
            content: content,
            createdAt: try FeedbackDate.parse(createdAt)
        )
    }
}

private struct FeedbackDetailResponse: Decodable {
    let feedbackId: String
    let content: String
    let createdAt: String
    let answerContent: String?
    let answeredAt: String?

    func detail() throws -> FeedbackDetail {
        guard UUID(uuidString: feedbackId) != nil else { throw FeedbackAPIError.invalidResponse }
        // 미답변·답변 삭제면 두 필드가 함께 null이다. 한쪽만 오면 보여줄 답변이 없는 것으로 본다.
        let answer: FeedbackAnswer? = if let answerContent, let answeredAt {
            FeedbackAnswer(content: answerContent, answeredAt: try FeedbackDate.parse(answeredAt))
        } else {
            nil
        }
        return FeedbackDetail(
            id: feedbackId,
            content: content,
            createdAt: try FeedbackDate.parse(createdAt),
            answer: answer
        )
    }
}

/// 서버 시각은 UTC ISO8601 Instant 문자열이다. 소수 초는 있을 수도 없을 수도 있다.
private enum FeedbackDate {
    static func parse(_ value: String) throws -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        if let date = formatter.date(from: value) { return date }
        throw FeedbackAPIError.invalidResponse
    }
}

private struct FeedbackSubmissionRequest: Encodable {
    let content: String
}

private struct FeedbackErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
}

enum FeedbackLimits {
    static let maximumContentLength = 1_000
}
