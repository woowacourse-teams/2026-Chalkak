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

    private func request(path: String, method: String = "GET") async throws -> Data {
        guard let baseURL, let authenticatedClient else {
            throw NotificationAPIError.configuration
        }
        let url = baseURL.appendingPathComponent(path)
        guard url.scheme?.lowercased() == "https" else {
            throw NotificationAPIError.configuration
        }
        var request = URLRequest(url: url)
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
