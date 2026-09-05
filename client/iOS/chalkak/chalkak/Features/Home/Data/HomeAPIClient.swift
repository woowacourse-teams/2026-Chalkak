import Foundation

struct HomeAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = HomeAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct HomeAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let configuration: HomeAPIConfiguration
    private let authenticatedClient: AuthenticatedHTTPClient
    private let decoder: JSONDecoder

    init(
        configuration: HomeAPIConfiguration = .development,
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

    func fetchHome(date: Date, sort: HomePostSort) async throws -> HomeViewState {
        let requestedDate = Self.apiDateFormatter.string(from: date)
        let topic: TopicResponse = try await request(
            path: "topics",
            queryItems: [URLQueryItem(name: "date", value: requestedDate)]
        )
        guard let topicDate = Self.apiDateFormatter.date(from: topic.topicDate) else {
            throw HomeAPIError.invalidResponse
        }

        let page = try await fetchPage(
            topicDate: topicDate,
            sort: sort,
            page: 1,
            randomSeed: nil
        )
        return HomeViewState(
            contentStatus: .content,
            topicDate: topicDate,
            topic: topic.title,
            photos: page.photos,
            selectedSort: sort,
            likedPhotoIDs: page.likedPhotoIDs,
            currentPage: page.currentPage,
            hasNext: page.hasNext,
            randomSeed: page.randomSeed
        )
    }

    func fetchNextPage(state: HomeViewState) async throws -> HomePage {
        guard let topicDate = state.topicDate else {
            throw HomeAPIError.invalidResponse
        }
        return try await fetchPage(
            topicDate: topicDate,
            sort: state.selectedSort,
            page: state.currentPage + 1,
            randomSeed: state.selectedSort == .random ? state.randomSeed : nil
        )
    }

    func updateLike(photoID: HomePhoto.ID, isLiked: Bool) async throws -> HomeLikeUpdate {
        let response: LikeResponse = try await request(
            path: "posts/\(photoID)/likes",
            method: isLiked ? "PUT" : "DELETE"
        )
        guard response.postID == photoID, response.likeCount >= 0 else {
            throw HomeAPIError.invalidResponse
        }
        return HomeLikeUpdate(
            photoID: response.postID,
            isLiked: response.isLiked,
            likeCount: response.likeCount
        )
    }

    private func fetchPage(
        topicDate: Date,
        sort: HomePostSort,
        page: Int,
        randomSeed: String?
    ) async throws -> HomePage {
        var queryItems = [
            URLQueryItem(name: "topicDate", value: Self.apiDateFormatter.string(from: topicDate)),
            URLQueryItem(name: "sort", value: sort.apiValue),
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: "20")
        ]
        if sort == .random, let randomSeed, !randomSeed.isEmpty {
            queryItems.append(URLQueryItem(name: "randomSeed", value: randomSeed))
        }

        let response: PostPageResponse = try await request(
            path: "posts",
            queryItems: queryItems
        )
        let effectiveSeed = sort == .random ? response.randomSeed ?? randomSeed : nil
        if sort == .random, response.hasNext, effectiveSeed?.isEmpty != false {
            throw HomeAPIError.invalidResponse
        }

        let photos = try response.posts.map { try $0.toHomePhoto() }
        return HomePage(
            photos: photos,
            likedPhotoIDs: Set(response.posts.filter(\.isLiked).map(\.id)),
            currentPage: response.currentPage,
            hasNext: response.hasNext,
            randomSeed: effectiveSeed
        )
    }

    private func request<Response: Decodable>(
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem] = []
    ) async throws -> Response {
        guard var components = URLComponents(
            url: configuration.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw HomeAPIError.invalidResponse
        }
        components.queryItems = queryItems.isEmpty ? nil : queryItems
        guard let url = components.url, url.scheme == "https" else {
            throw HomeAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, httpResponse) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw HomeAPIError.http(httpResponse.statusCode)
            }
            guard !data.isEmpty else {
                throw HomeAPIError.invalidResponse
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw HomeAPIError.invalidResponse
            }
        } catch let error as HomeAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw HomeAPIError.http(401)
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw HomeAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw HomeAPIError.network
        }
    }

    private static let apiDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

enum HomeAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)

    var initialError: HomeInitialError {
        switch self {
        case .network:
            .network
        case .invalidResponse:
            .invalidResponse
        case let .http(statusCode):
            switch statusCode {
            case 401:
                .unauthorized
            case 404:
                .topicNotFound
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

private extension HomePostSort {
    var apiValue: String {
        switch self {
        case .latest:
            "recent"
        case .popular:
            "popular"
        case .random:
            "random"
        }
    }
}

private struct TopicResponse: Decodable {
    let title: String
    let topicDate: String
}

private struct PostPageResponse: Decodable {
    let currentPage: Int
    let hasNext: Bool
    let randomSeed: String?
    let posts: [PostResponse]
}

private struct PostResponse: Decodable {
    let id: String
    let originalImageURL: String
    let signatureOriginalImageURL: String
    let title: String?
    let likeCount: Int
    let isLiked: Bool
    let isMine: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case originalImageURL = "originalImageUrl"
        case signatureOriginalImageURL = "signatureOriginalImageUrl"
        case title
        case likeCount
        case isLiked
        case isMine
    }

    func toHomePhoto() throws -> HomePhoto {
        guard likeCount >= 0,
              let imageURL = URL(string: originalImageURL),
              let signatureURL = URL(string: signatureOriginalImageURL)
        else {
            throw HomeAPIError.invalidResponse
        }
        let normalizedTitle = title?.trimmingCharacters(in: .whitespacesAndNewlines)
        let contentDescription = normalizedTitle
            .flatMap { $0.isEmpty ? nil : $0 }
            .map { "작품 이미지: \($0)" }
            ?? "무제 작품 이미지"
        return HomePhoto(
            id: id,
            imageSource: .remote(imageURL),
            signatureSource: .remote(signatureURL),
            contentDescription: contentDescription,
            title: title,
            likeCount: likeCount,
            isOwnedByCurrentUser: isMine ?? false
        )
    }
}

private struct LikeResponse: Decodable {
    let postID: String
    let likeCount: Int
    let isLiked: Bool

    enum CodingKeys: String, CodingKey {
        case postID = "postId"
        case likeCount
        case isLiked
    }
}
