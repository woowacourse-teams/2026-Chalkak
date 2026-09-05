import Foundation

struct FeedAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = FeedAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct FeedAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let configuration: FeedAPIConfiguration
    private let authenticatedClient: AuthenticatedHTTPClient
    private let decoder: JSONDecoder

    init(
        configuration: FeedAPIConfiguration = .development,
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
    }

    func fetchPostDetail(postID: String) async throws -> FeedContent {
        let response: FeedPostDetailResponse = try await request(path: "posts/\(postID)")
        return try response.toFeedContent()
    }

    func updateLike(postID: String, isLiked: Bool) async throws -> FeedLikeUpdate {
        let response: FeedLikeResponse = try await request(
            path: "posts/\(postID)/likes",
            method: isLiked ? "PUT" : "DELETE"
        )
        guard response.postID == postID, response.likeCount >= 0 else {
            throw FeedAPIError.invalidResponse
        }
        return FeedLikeUpdate(
            postID: response.postID,
            isLiked: response.isLiked,
            likeCount: response.likeCount
        )
    }

    private func request<Response: Decodable>(
        path: String,
        method: String = "GET"
    ) async throws -> Response {
        guard let url = URL(string: path, relativeTo: configuration.baseURL),
              url.scheme == "https"
        else {
            throw FeedAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, httpResponse) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw FeedAPIError.http(httpResponse.statusCode)
            }
            guard !data.isEmpty else {
                throw FeedAPIError.invalidResponse
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw FeedAPIError.invalidResponse
            }
        } catch let error as FeedAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw FeedAPIError.http(401)
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw FeedAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw FeedAPIError.network
        }
    }
}

enum FeedAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)

    var feedError: FeedError {
        switch self {
        case .network:
            .network
        case .invalidResponse:
            .invalidResponse
        case let .http(statusCode):
            switch statusCode {
            case 404:
                .notFound
            case 400..<500:
                .client
            case 500..<600:
                .server
            default:
                .generic
            }
        }
    }
}
