import Foundation

struct AIReviewAPIClient: Sendable {
    private let baseURL: URL
    private let authenticatedClient: AuthenticatedHTTPClient

    init(
        baseURL: URL,
        session: URLSession = .shared,
        accessTokenProvider: @escaping @Sendable () async -> String? = {
            await MainActor.run { KeychainSessionStore.accessToken() }
        }
    ) {
        self.baseURL = baseURL
        self.authenticatedClient = AuthenticatedHTTPClient(
            baseURL: baseURL,
            session: session,
            sessionStore: .live(accessTokenProvider: accessTokenProvider)
        )
    }

    func fetchReview(postID: String) async throws -> AIReview {
        try await request(postID: postID, method: "GET")
    }

    func requestReview(postID: String) async throws -> AIReview {
        try await request(postID: postID, method: "POST")
    }

    private func request(postID: String, method: String) async throws -> AIReview {
        guard !postID.isEmpty, !postID.contains("/"),
              baseURL.scheme?.lowercased() == "https", baseURL.host?.isEmpty == false else {
            throw AIReviewError.invalidResponse
        }
        let url = baseURL.appendingPathComponent("posts")
            .appendingPathComponent(postID)
            .appendingPathComponent("ai-review")
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                if response.statusCode == 401 { throw AIReviewError.unauthorized }
                let error = try? JSONDecoder().decode(ErrorResponse.self, from: data)
                throw AIReviewError.http(statusCode: response.statusCode, message: error?.message)
            }
            do {
                let review = try JSONDecoder().decode(AIReview.self, from: data)
                if review.status == .completed {
                    guard let result = review.result,
                          (0...10).contains(result.overallScore),
                          result.criteria.allSatisfy({ (0...10).contains($0.score) }) else {
                        throw AIReviewError.invalidResponse
                    }
                }
                return review
            } catch {
                throw AIReviewError.invalidResponse
            }
        } catch let error as AIReviewError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw AIReviewError.unauthorized
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw AIReviewError.invalidResponse
        } catch {
            throw AIReviewError.network
        }
    }

    private struct ErrorResponse: Decodable {
        let message: String?
    }
}
