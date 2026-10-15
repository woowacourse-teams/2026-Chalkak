import Foundation

struct NotificationSettingsAPIClient: Sendable {
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

    func fetch() async throws -> NotificationSettings {
        let data = try await request(method: "GET")
        guard !data.isEmpty else { throw SettingsAPIError.invalidResponse }
        do { return try decoder.decode(NotificationSettings.self, from: data) }
        catch { throw SettingsAPIError.invalidResponse }
    }

    /// Sends only the changed preference to avoid overwriting another device's update.
    /// The endpoint returns 204 with no response body.
    func update(topicPushEnabled: Bool? = nil, moderationPushEnabled: Bool? = nil) async throws {
        guard topicPushEnabled != nil || moderationPushEnabled != nil else { return }
        let body = try JSONEncoder().encode(
            NotificationSettingsUpdateRequest(
                topicPushEnabled: topicPushEnabled,
                moderationPushEnabled: moderationPushEnabled
            )
        )
        _ = try await request(method: "PATCH", body: body)
    }

    private func request(method: String, body: Data? = nil) async throws -> Data {
        guard let baseURL, let authenticatedClient else { throw SettingsAPIError.configuration }
        let url = baseURL.appendingPathComponent("notification-settings")
        guard url.scheme?.lowercased() == "https" else { throw SettingsAPIError.configuration }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        do {
            let (data, response) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(response.statusCode) else {
                if response.statusCode == 401 { throw SettingsAPIError.unauthorized }
                throw SettingsAPIError.http(response.statusCode)
            }
            return data
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw SettingsAPIError.unauthorized
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw SettingsAPIError.invalidResponse
        } catch let error as SettingsAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw SettingsAPIError.network
        }
    }
}

private struct NotificationSettingsUpdateRequest: Encodable {
    let topicPushEnabled: Bool?
    let moderationPushEnabled: Bool?
}
