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

        let url = configuration.baseURL.appendingPathComponent("feedbacks")
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false else {
            throw FeedbackAPIError.configuration
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                throw apiError(statusCode: response.statusCode, data: data)
            }
            guard !data.isEmpty else {
                throw FeedbackAPIError.invalidResponse
            }

            do {
                let response = try decoder.decode(FeedbackSubmissionResponse.self, from: data)
                guard !response.feedbackID.isEmpty, !response.createdAt.isEmpty else {
                    throw FeedbackAPIError.invalidResponse
                }
                return response
            } catch let error as FeedbackAPIError {
                throw error
            } catch {
                throw FeedbackAPIError.invalidResponse
            }
        } catch let error as FeedbackAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw FeedbackAPIError.unauthorized
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw FeedbackAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw FeedbackAPIError.network
        }
    }

    @discardableResult
    func submit(content: String) async throws -> FeedbackSubmissionResponse {
        try await submitFeedback(content: content)
    }

    private func apiError(statusCode: Int, data: Data) -> FeedbackAPIError {
        if statusCode == 401 {
            return .unauthorized
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
