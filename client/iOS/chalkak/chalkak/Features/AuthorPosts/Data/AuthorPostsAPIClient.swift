import Foundation

struct AuthorPostsAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = AuthorPostsAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

/// 작가가 공개한 사진 목록을 페이지 단위로 조회한다. 로그인이 필요하다(POST-007).
struct AuthorPostsAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    static let pageSize = 20

    private let configuration: AuthorPostsAPIConfiguration
    private let authenticatedClient: AuthenticatedHTTPClient
    private let decoder: JSONDecoder

    init(
        configuration: AuthorPostsAPIConfiguration = .development,
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

    /// 예) fetchPage(authorID: "0198…a1", page: 2) → GET /users/0198…a1/posts?page=2&pageSize=20
    func fetchPage(authorID: String, page: Int) async throws -> AuthorPostsPage {
        guard !authorID.isEmpty, page >= 1 else {
            throw AuthorPostsAPIError.invalidResponse
        }
        let path = configuration.baseURL
            .appendingPathComponent("users")
            .appendingPathComponent(authorID)
            .appendingPathComponent("posts")
        guard var components = URLComponents(url: path, resolvingAgainstBaseURL: false) else {
            throw AuthorPostsAPIError.invalidResponse
        }
        components.queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(Self.pageSize))
        ]
        guard let url = components.url, url.scheme == "https" else {
            throw AuthorPostsAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let response: AuthorPostPageResponse = try await send(request)
        return AuthorPostsPage(
            posts: try response.posts.map { try $0.toAuthorPost() },
            currentPage: response.currentPage,
            hasNext: response.hasNext
        )
    }

    private func send<Response: Decodable>(_ request: URLRequest) async throws -> Response {
        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                throw AuthorPostsAPIError.http(response.statusCode)
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw AuthorPostsAPIError.invalidResponse
            }
        } catch let error as AuthorPostsAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw AuthorPostsAPIError.http(401)
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw AuthorPostsAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw AuthorPostsAPIError.network
        }
    }
}

enum AuthorPostsAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)

    var authorPostsError: AuthorPostsError {
        switch self {
        case .network:
            .network
        case .invalidResponse:
            .generic
        case let .http(statusCode):
            switch statusCode {
            case 401:
                .unauthorized
            case 404:
                .authorNotFound
            default:
                .generic
            }
        }
    }
}
