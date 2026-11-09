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

    /// 제목·설명처럼 비워 둘 수 있는 글은 앞뒤 공백을 지우고, 남는 글이 없으면 nil(없음)로 본다(POST-005).
    /// 예: "  퇴근길  " → "퇴근길", "   \n" → nil
    private static func normalizedOptionalText(_ text: String?) -> String? {
        guard let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }

    func updatePost(
        postID: String,
        title: String?,
        description: String?,
        instagramConsent: InstagramConsent = .notAgreed
    ) async throws -> FeedPostUpdate {
        let body: Data
        do {
            body = try encoder.encode(
                FeedPostUpdateRequest(
                    title: Self.normalizedOptionalText(title),
                    description: Self.normalizedOptionalText(description),
                    instagramConsent: instagramConsent
                )
            )
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
                // ID 형식 오류는 errorCode가 다른 400과 같아(BUSINESS_ERROR) message로만 구분한다.
                let errorMessage = (try? decoder.decode(FeedAPIErrorResponse.self, from: data))?.message
                if response.statusCode == 400,
                   errorMessage == InstagramConsentInput.serverInvalidInstagramIDMessage {
                    throw FeedAPIError.invalidInstagramID
                }
                throw FeedAPIError.http(response.statusCode)
            }
            guard !data.isEmpty else {
                throw FeedAPIError.invalidResponse
            }

            let decoded: FeedPostUpdateResponse
            do {
                decoded = try decoder.decode(FeedPostUpdateResponse.self, from: data)
            } catch {
                throw FeedAPIError.invalidResponse
            }
            guard decoded.postID == postID else {
                throw FeedAPIError.invalidResponse
            }
            // 상세 조회와 같이 공백뿐인 설명은 설명 없음으로 본다.
            let updatedDescription = decoded.description.flatMap {
                $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0
            }
            return FeedPostUpdate(
                postID: decoded.postID,
                title: Self.normalizedOptionalText(decoded.title),
                description: updatedDescription,
                instagramConsent: decoded.instagramUploadAgreed.map {
                    InstagramConsent(isAgreed: $0, instagramID: $0 ? decoded.instagramID : nil)
                }
            )
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

/// 서버 오류 응답 본문. 예: `{"errorCode":"BUSINESS_ERROR","message":"인스타그램 ID 형식이 올바르지 않습니다."}`
private struct FeedAPIErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
}

enum FeedAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)
    // 수정 저장에서 서버가 인스타그램 ID 형식을 거절했다.
    case invalidInstagramID

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
        case .invalidInstagramID:
            .invalidInstagramID
        }
    }
}
