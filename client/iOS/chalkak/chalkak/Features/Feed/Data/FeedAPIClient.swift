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
    private let encoder: JSONEncoder

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
        self.encoder = JSONEncoder()
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

    func deletePost(postID: String) async throws {
        try await requestNoContent(path: "posts/\(postID)", method: "DELETE")
    }

    func updatePostTitle(postID: String, title: String?) async throws -> FeedTitleUpdate {
        let normalizedTitle: String?
        if let title {
            let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            normalizedTitle = trimmedTitle.isEmpty ? nil : trimmedTitle
        } else {
            normalizedTitle = nil
        }
        let body: Data
        do {
            body = try encoder.encode(FeedTitleUpdateRequest(title: normalizedTitle))
        } catch {
            throw FeedAPIError.invalidResponse
        }

        let url = configuration.baseURL.appendingPathComponent("posts/\(postID)")
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false else {
            throw FeedAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                throw FeedAPIError.http(response.statusCode)
            }
            guard !data.isEmpty else {
                throw FeedAPIError.invalidResponse
            }

            let decoded: FeedTitleUpdateResponse
            do {
                decoded = try decoder.decode(FeedTitleUpdateResponse.self, from: data)
            } catch {
                throw FeedAPIError.invalidResponse
            }
            guard decoded.postID == postID else {
                throw FeedAPIError.invalidResponse
            }
            let normalizedTitle: String?
            if let title = decoded.title {
                let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
                normalizedTitle = trimmedTitle.isEmpty ? nil : trimmedTitle
            } else {
                normalizedTitle = nil
            }
            return FeedTitleUpdate(postID: decoded.postID, title: normalizedTitle)
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

    private func request<Response: Decodable>(
        path: String,
        method: String = "GET"
    ) async throws -> Response {
        let url = configuration.baseURL.appendingPathComponent(path)
        guard url.scheme == "https"
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

    private func requestNoContent(
        path: String,
        method: String
    ) async throws {
        let url = configuration.baseURL.appendingPathComponent(path)
        guard url.scheme == "https"
        else {
            throw FeedAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (_, httpResponse) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw FeedAPIError.http(httpResponse.statusCode)
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
