import Foundation

struct NotificationAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let baseURL: URL?
    private let authenticatedClient: AuthenticatedHTTPClient?
    private let decoder = JSONDecoder()

    init(
        baseURL: URL?,
        session: URLSession = .shared,
        accessTokenProvider: @escaping AccessTokenProvider
    ) {
        self.baseURL = baseURL
        self.authenticatedClient = baseURL.map {
            AuthenticatedHTTPClient(
                baseURL: $0,
                session: session,
                sessionStore: .live(accessTokenProvider: accessTokenProvider)
            )
        }
    }

    func fetchPage(page: Int, pageSize: Int = 20) async throws -> NotificationPage {
        guard page >= 1, pageSize > 0 else { throw NotificationAPIError.http(400) }
        let data = try await request(path: "notifications", queryItems: [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize))
        ])
        do {
            let response = try decoder.decode(NotificationPageResponse.self, from: data)
            guard response.currentPage == page, response.pageSize > 0 else {
                throw NotificationAPIError.invalidResponse
            }
            return NotificationPage(currentPage: response.currentPage, pageSize: response.pageSize,
                                    hasNext: response.hasNext, items: try response.notifications.map { try $0.item() })
        } catch { throw NotificationAPIError.invalidResponse }
    }

    func fetchUnreadStatus() async throws -> Bool {
        let data = try await request(path: "notifications/unread-status")
        do { return try decoder.decode(NotificationUnreadResponse.self, from: data).hasUnread }
        catch { throw NotificationAPIError.invalidResponse }
    }

    func fetchDetail(notificationID: String) async throws -> NotificationDetail {
        let data = try await request(path: "notifications/\(notificationID)")
        do {
            return try decoder.decode(NotificationDetailResponse.self, from: data).detail
        } catch {
            throw NotificationAPIError.invalidResponse
        }
    }

    /// 성공은 본문 없는 204이며 이미 읽은 알림에 반복 요청해도 된다.
    func markRead(notificationID: String) async throws {
        _ = try await request(path: "notifications/\(notificationID)/read", method: "PATCH")
    }

    /// 알림함에 표시되는 미읽음 알림을 모두 읽음 처리한다. 성공 응답은 본문 없는 204다.
    func markAllRead() async throws {
        _ = try await request(path: "notifications/read-all", method: "PATCH")
    }

    private func request(path: String, method: String = "GET", queryItems: [URLQueryItem] = []) async throws -> Data {
        guard let baseURL, let authenticatedClient else {
            throw NotificationAPIError.configuration
        }
        let url = baseURL.appendingPathComponent(path)
        guard url.scheme?.lowercased() == "https" else {
            throw NotificationAPIError.configuration
        }
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw NotificationAPIError.configuration
        }
        if !queryItems.isEmpty { components.queryItems = queryItems }
        guard let requestURL = components.url else { throw NotificationAPIError.configuration }
        var request = URLRequest(url: requestURL)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                switch response.statusCode {
                case 401:
                    throw NotificationAPIError.unauthorized
                case 404:
                    throw NotificationAPIError.notFound
                default:
                    throw NotificationAPIError.http(response.statusCode)
                }
            }
            return data
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw NotificationAPIError.unauthorized
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw NotificationAPIError.invalidResponse
        } catch let error as NotificationAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw NotificationAPIError.network
        }
    }
}

enum NotificationAPIError: Error, Equatable, Sendable {
    case configuration
    case network
    case invalidResponse
    case unauthorized
    case notFound
    case http(Int)
}

private struct NotificationDetailResponse: Decodable {
    let id: String
    let title: String
    let body: String
    let originalImageUrl: String?
    let rejectionReason: String?

    var detail: NotificationDetail {
        NotificationDetail(
            id: id,
            title: title,
            body: body,
            originalImageURL: originalImageUrl.flatMap(Self.imageURL),
            rejectionReason: rejectionReason
        )
    }

    private static func imageURL(_ value: String) -> URL? {
        guard let url = URL(string: value),
              url.scheme?.lowercased() == "https",
              url.host?.isEmpty == false else {
            return nil
        }
        return url
    }
}

private struct NotificationUnreadResponse: Decodable {
    let hasUnread: Bool
}

private struct NotificationPageResponse: Decodable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool
    let notifications: [NotificationItemResponse]
}

private struct NotificationItemResponse: Decodable {
    let id: String
    let type: String
    let sourceType: String?
    let sourceId: String?
    let title: String
    let body: String
    let thumbnailImageUrl: String?
    let readAt: String?
    let createdAt: String

    func item() throws -> NotificationItem {
        guard UUID(uuidString: id) != nil, let created = Self.date(createdAt) else {
            throw NotificationAPIError.invalidResponse
        }
        let read: Date?
        if let readAt {
            guard let parsed = Self.date(readAt) else { throw NotificationAPIError.invalidResponse }
            read = parsed
        } else { read = nil }
        let thumbnail = thumbnailImageUrl.flatMap { value -> URL? in
            guard let url = URL(string: value), url.scheme?.lowercased() == "https", url.host != nil else { return nil }
            return url
        }
        return NotificationItem(id: id, type: type, sourceType: sourceType, sourceID: sourceId,
                                title: title, body: body, thumbnailURL: thumbnail, readAt: read, createdAt: created)
    }

    private static func date(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
