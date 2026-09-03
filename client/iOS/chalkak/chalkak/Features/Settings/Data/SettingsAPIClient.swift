import Foundation

struct SettingsAPIClient: Sendable {
    typealias AccessTokenProvider = @Sendable () async -> String?

    private let baseURL: URL?
    private let session: URLSession
    private let accessTokenProvider: AccessTokenProvider
    private let decoder = JSONDecoder()

    init(
        baseURL: URL?,
        session: URLSession = .shared,
        accessTokenProvider: @escaping AccessTokenProvider
    ) {
        self.baseURL = baseURL
        self.session = session
        self.accessTokenProvider = accessTokenProvider
    }

    func fetchSignature() async throws -> URL? {
        let data = try await request(path: "users/me/signature")
        let response: UserSignatureResponse
        do {
            response = try decoder.decode(UserSignatureResponse.self, from: data)
        } catch {
            throw SettingsAPIError.invalidResponse
        }

        guard let url = URL(string: response.signatureThumbnailImageURL),
              url.scheme?.lowercased() == "https",
              url.host?.isEmpty == false
        else {
            throw SettingsAPIError.invalidResponse
        }
        return url
    }

    func withdraw() async throws {
        _ = try await request(path: "users/me", method: "DELETE")
    }

    func updateSignature(pngData: Data) async throws -> URL {
        guard pngData.count <= Metrics.maximumSignatureBytes else {
            throw SettingsAPIError.signatureTooLarge
        }

        let uploadRequestData = try await request(
            path: "users/me/signature/uploads",
            method: "POST"
        )
        let uploadResponse: SignatureUploadResponse
        do {
            uploadResponse = try decoder.decode(SignatureUploadResponse.self, from: uploadRequestData)
        } catch {
            throw SettingsAPIError.invalidResponse
        }

        guard !uploadResponse.uploadId.isEmpty,
              let uploadURL = URL(string: uploadResponse.uploadUrl),
              uploadURL.scheme?.lowercased() == "https",
              uploadURL.host?.isEmpty == false
        else {
            throw SettingsAPIError.invalidResponse
        }

        try await upload(pngData, to: uploadURL)

        let updateBody: Data
        do {
            updateBody = try JSONEncoder().encode(
                SignatureUpdateRequest(signatureOriginalUploadId: uploadResponse.uploadId)
            )
        } catch {
            throw SettingsAPIError.invalidResponse
        }
        let updateData = try await request(
            path: "users/me/signature",
            method: "PUT",
            body: updateBody,
            contentType: "application/json"
        )
        let updateResponse: SignatureUpdateResponse
        do {
            updateResponse = try decoder.decode(SignatureUpdateResponse.self, from: updateData)
        } catch {
            throw SettingsAPIError.invalidResponse
        }

        guard let signatureURL = URL(string: updateResponse.signatureOriginalImageUrl),
              signatureURL.scheme?.lowercased() == "https",
              signatureURL.host?.isEmpty == false
        else {
            throw SettingsAPIError.invalidResponse
        }
        return signatureURL
    }

    private func request(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        contentType: String? = nil
    ) async throws -> Data {
        guard let baseURL else {
            throw SettingsAPIError.configuration
        }
        let url = baseURL.appendingPathComponent(path)
        guard url.scheme?.lowercased() == "https" else {
            throw SettingsAPIError.configuration
        }
        guard let token = await accessTokenProvider(), !token.isEmpty else {
            throw SettingsAPIError.unauthorized
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }

        return try await data(for: request)
    }

    private func upload(_ data: Data, to url: URL) async throws {
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.httpBody = data
        request.setValue("image/png", forHTTPHeaderField: "Content-Type")
        _ = try await self.data(for: request)
    }

    private func data(for request: URLRequest) async throws -> Data {
        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw SettingsAPIError.invalidResponse
            }
            guard (200..<300).contains(httpResponse.statusCode) else {
                if httpResponse.statusCode == 401 {
                    throw SettingsAPIError.unauthorized
                }
                throw SettingsAPIError.http(httpResponse.statusCode)
            }
            return data
        } catch let error as SettingsAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw SettingsAPIError.network
        }
    }
}

enum SettingsAPIError: Error, Equatable, Sendable {
    case configuration
    case network
    case invalidResponse
    case unauthorized
    case signatureTooLarge
    case http(Int)
}

private struct UserSignatureResponse: Decodable {
    let signatureThumbnailImageURL: String

    enum CodingKeys: String, CodingKey {
        case signatureThumbnailImageURL = "signatureThumbnailImageUrl"
    }
}

private struct SignatureUploadResponse: Decodable {
    let uploadId: String
    let uploadUrl: String
    let expiresInSeconds: Int
}

private struct SignatureUpdateRequest: Encodable {
    let signatureOriginalUploadId: String
}

private struct SignatureUpdateResponse: Decodable {
    let signatureOriginalImageUrl: String
}

private enum Metrics {
    static let maximumSignatureBytes = 1_048_576
}
