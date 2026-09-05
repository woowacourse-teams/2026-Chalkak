import Foundation

struct PhotoUploadAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = PhotoUploadAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct PhotoUploadAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let configuration: PhotoUploadAPIConfiguration
    private let session: URLSession
    private let authenticatedClient: AuthenticatedHTTPClient
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(
        configuration: PhotoUploadAPIConfiguration = .development,
        session: URLSession = .shared,
        accessTokenProvider: @escaping AccessTokenProvider = { nil }
    ) {
        self.configuration = configuration
        self.session = session
        self.authenticatedClient = AuthenticatedHTTPClient(
            baseURL: configuration.baseURL,
            session: session,
            sessionStore: .live(accessTokenProvider: accessTokenProvider)
        )
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    func fetchTopic(date: Date) async throws -> PhotoUploadTopic {
        let requestedDate = PhotoUploadDate.apiString(from: date)
        let response: TopicResponse = try await request(
            path: "topics",
            queryItems: [
                URLQueryItem(name: "date", value: requestedDate)
            ]
        )
        guard response.topicDate == requestedDate,
              response.id.isEmpty == false,
              response.title.isEmpty == false
        else {
            throw PhotoUploadAPIError.invalidResponse
        }

        return PhotoUploadTopic(
            id: response.id,
            title: response.title,
            date: PhotoUploadDate.startOfDay(date)
        )
    }

    func createPostImageUpload() async throws -> PhotoUploadUploadPolicy {
        let response: PostImageUploadResponse = try await request(
            path: "posts/uploads",
            method: "POST"
        )
        guard let uploadURL = URL(string: response.uploadURL),
              uploadURL.scheme?.lowercased() == "https",
              response.uploadID.isEmpty == false,
              response.expiresInSeconds > 0,
              response.maxBytes > 0,
              response.contentType.split(separator: ";", maxSplits: 1)
                .first?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased() == "image/webp"
        else {
            throw PhotoUploadAPIError.invalidResponse
        }

        return PhotoUploadUploadPolicy(
            uploadID: response.uploadID,
            uploadURL: uploadURL,
            expiresInSeconds: response.expiresInSeconds,
            contentType: response.contentType,
            maxBytes: response.maxBytes
        )
    }

    func upload(
        data: Data,
        to uploadURL: URL,
        contentType: String
    ) async throws {
        var request = URLRequest(url: uploadURL)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.httpBody = data

        do {
            let (_, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw PhotoUploadAPIError.invalidResponse
            }
            guard (200..<300).contains(httpResponse.statusCode) else {
                throw PhotoUploadAPIError.http(
                    statusCode: httpResponse.statusCode,
                    message: nil
                )
            }
        } catch let error as PhotoUploadAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw PhotoUploadAPIError.network
        }
    }

    func createPost(
        topicID: String,
        photoUploadID: String,
        title: String?
    ) async throws -> PhotoUploadCreationResponse {
        let body = try encoder.encode(
            PostCreateRequest(
                topicID: topicID,
                photoUploadID: photoUploadID,
                title: title
            )
        )
        return try await request(
            path: "posts",
            method: "POST",
            body: body
        )
    }

    private func request<Response: Decodable>(
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem] = [],
        body: Data? = nil
    ) async throws -> Response {
        guard var components = URLComponents(
            url: configuration.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw PhotoUploadAPIError.invalidResponse
        }
        components.queryItems = queryItems.isEmpty ? nil : queryItems
        guard let url = components.url, url.scheme?.lowercased() == "https" else {
            throw PhotoUploadAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }

        do {
            let (data, httpResponse) = try await authenticatedClient.data(for: request)
            guard (200..<300).contains(httpResponse.statusCode) else {
                let errorResponse = try? decoder.decode(APIErrorResponse.self, from: data)
                throw PhotoUploadAPIError.http(
                    statusCode: httpResponse.statusCode,
                    message: errorResponse?.message
                )
            }
            guard data.isEmpty == false else {
                throw PhotoUploadAPIError.invalidResponse
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw PhotoUploadAPIError.invalidResponse
            }
        } catch let error as PhotoUploadAPIError {
            throw error
        } catch AuthenticatedHTTPClientError.reauthenticationRequired {
            throw PhotoUploadAPIError.http(statusCode: 401, message: nil)
        } catch AuthenticatedHTTPClientError.invalidResponse {
            throw PhotoUploadAPIError.invalidResponse
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw PhotoUploadAPIError.network
        }
    }
}

enum PhotoUploadAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(statusCode: Int, message: String?)
}

struct PhotoUploadCreationResponse: Equatable, Sendable, Decodable {
    let postID: String
    let moderationStatus: String

    enum CodingKeys: String, CodingKey {
        case postID = "postId"
        case moderationStatus
    }
}

private struct TopicResponse: Decodable {
    let id: String
    let title: String
    let topicDate: String
}

private struct PostImageUploadResponse: Decodable {
    let uploadID: String
    let uploadURL: String
    let expiresInSeconds: Int64
    let contentType: String
    let maxBytes: Int64

    enum CodingKeys: String, CodingKey {
        case uploadID = "uploadId"
        case uploadURL = "uploadUrl"
        case expiresInSeconds
        case contentType
        case maxBytes
    }
}

private struct PostCreateRequest: Encodable {
    let topicID: String
    let photoUploadID: String
    let title: String?

    enum CodingKeys: String, CodingKey {
        case topicID = "topicId"
        case photoUploadID = "photoUploadId"
        case title
    }
}

private struct APIErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
}
