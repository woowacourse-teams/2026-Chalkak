import Foundation
import OSLog

struct HomeData: Equatable {
    let topicDate: String
    let topic: String
    let photos: [HomePhoto]
}

struct HomePhoto: Decodable, Equatable, Identifiable {
    let id: String
    let originalImageURL: URL?
    let thumbnailImageURL: URL?
    let signatureOriginalImageURL: URL?
    let signatureThumbnailImageURL: URL?
    let title: String?
    let likeCount: Int
    let isLiked: Bool

    private enum CodingKeys: String, CodingKey {
        case id
        case originalImageURL = "originalImageUrl"
        case thumbnailImageURL = "thumbnailImageUrl"
        case signatureOriginalImageURL = "signatureOriginalImageUrl"
        case signatureThumbnailImageURL = "signatureThumbnailImageUrl"
        case title
        case likeCount
        case isLiked
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        originalImageURL = URL(string: try container.decode(String.self, forKey: .originalImageURL))
        thumbnailImageURL = URL(string: try container.decode(String.self, forKey: .thumbnailImageURL))
        signatureOriginalImageURL = URL(
            string: try container.decode(String.self, forKey: .signatureOriginalImageURL)
        )
        signatureThumbnailImageURL = URL(
            string: try container.decode(String.self, forKey: .signatureThumbnailImageURL)
        )
        title = try container.decodeIfPresent(String.self, forKey: .title)
        likeCount = try container.decode(Int.self, forKey: .likeCount)
        isLiked = try container.decode(Bool.self, forKey: .isLiked)
    }
}

protocol HomeRepository {
    func fetchHome(for date: String) async throws -> HomeData
}

final class APIHomeRepository: HomeRepository {
    private let baseURL: URL?
    private let session: URLSession
    private let decoder = JSONDecoder()
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "HomeAPI"
    )

    init(baseURL: URL?, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func fetchHome(for date: String) async throws -> HomeData {
        let topic: TopicResponse = try await request(
            path: "topics",
            queryItems: [URLQueryItem(name: "date", value: date)]
        )
        guard !topic.title.isEmpty, !topic.topicDate.isEmpty else {
            throw HomeRepositoryError.invalidResponse
        }

        let page: PostPageResponse = try await request(
            path: "posts",
            queryItems: [
                URLQueryItem(name: "topicDate", value: topic.topicDate),
                // Android maps PostSort.LATEST to the backend value "recent".
                URLQueryItem(name: "sort", value: "recent"),
                URLQueryItem(name: "page", value: "1"),
                URLQueryItem(name: "pageSize", value: "20"),
            ]
        )

        logger.debug(
            "Home loaded topicDate=\(topic.topicDate, privacy: .public), photoCount=\(page.posts.count, privacy: .public)"
        )
        return HomeData(
            topicDate: topic.topicDate,
            topic: topic.title,
            photos: page.posts
        )
    }

    private func request<Response: Decodable>(
        path: String,
        queryItems: [URLQueryItem]
    ) async throws -> Response {
        guard let baseURL else {
            throw HomeRepositoryError.configuration
        }

        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw HomeRepositoryError.configuration
        }
        components.queryItems = queryItems
        guard let url = components.url else {
            throw HomeRepositoryError.configuration
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let accessToken = KeychainSessionStore.accessToken() {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        logger.debug("Home request url=\(url.absoluteString, privacy: .public)")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            logger.error(
                "Home transport error: \(String(describing: error), privacy: .public)"
            )
            throw HomeRepositoryError.requestFailed
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw HomeRepositoryError.requestFailed
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            let serverError = try? decoder.decode(HomeAPIErrorResponse.self, from: data)
            logger.error(
                "Home response status=\(httpResponse.statusCode, privacy: .public), errorCode=\(serverError?.errorCode ?? "unknown", privacy: .public), message=\(serverError?.message ?? "unknown", privacy: .public)"
            )
            throw HomeRepositoryError.server(
                statusCode: httpResponse.statusCode,
                message: serverError?.message
            )
        }

        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            logger.error(
                "Home response decode error: \(String(describing: error), privacy: .public)"
            )
            throw HomeRepositoryError.invalidResponse
        }
    }
}

private struct TopicResponse: Decodable {
    let title: String
    let topicDate: String
}

private struct PostPageResponse: Decodable {
    let posts: [HomePhoto]
}

private struct HomeAPIErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
}

enum HomeRepositoryError: LocalizedError {
    case configuration
    case invalidResponse
    case requestFailed
    case server(statusCode: Int, message: String?)

    var errorDescription: String? {
        switch self {
        case .configuration:
            "홈 API 설정을 확인해 주세요."
        case .invalidResponse:
            "홈 응답을 확인할 수 없어요."
        case .requestFailed:
            "홈을 불러오지 못했어요. 네트워크 연결을 확인해 주세요."
        case let .server(_, message):
            message?.isEmpty == false ? message : "홈을 불러오지 못했어요."
        }
    }
}
