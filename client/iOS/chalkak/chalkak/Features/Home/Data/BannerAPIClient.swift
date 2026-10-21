import Foundation

struct BannerAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = BannerAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct BannerAPIClient: Sendable {
    private let configuration: BannerAPIConfiguration
    private let session: URLSession

    init(
        configuration: BannerAPIConfiguration = .development,
        session: URLSession = .shared
    ) {
        self.configuration = configuration
        self.session = session
    }

    func fetchPage(page: Int = 1, pageSize: Int = 10) async throws -> BannerPage {
        let url = configuration.baseURL.appendingPathComponent("banners")
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            throw BannerAPIError.invalidResponse
        }
        components.queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize))
        ]
        guard let requestURL = components.url else {
            throw BannerAPIError.invalidResponse
        }
        var request = URLRequest(url: requestURL)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        // Public banners do not attach session credentials or trigger token refresh.
        do {
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else {
                throw BannerAPIError.invalidResponse
            }
            guard (200..<300).contains(response.statusCode) else {
                throw BannerAPIError.http(response.statusCode)
            }
            do {
                return try JSONDecoder().decode(BannerPageResponse.self, from: data).toBannerPage()
            } catch {
                throw BannerAPIError.invalidResponse
            }
        } catch let error as BannerAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw BannerAPIError.network
        }
    }
}

enum BannerAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)
}

private struct BannerPageResponse: Decodable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool?
    let banners: [BannerResponse]

    func toBannerPage() throws -> BannerPage {
        BannerPage(
            currentPage: currentPage,
            pageSize: pageSize,
            hasNext: hasNext ?? false,
            banners: try banners.map { try $0.toBanner() }
        )
    }
}

private struct BannerResponse: Decodable {
    let id: String
    let title: String
    let subtitle: String
    let webViewUrl: String
    let thumbnailUrl: String
    let createdAt: String

    func toBanner() throws -> Banner {
        guard let webViewURL = URL(string: webViewUrl),
              webViewURL.scheme?.lowercased() == "https", webViewURL.host?.isEmpty == false
        else {
            throw BannerAPIError.invalidResponse
        }
        let thumbnailURL = URL(string: thumbnailUrl).flatMap { url in
            url.scheme?.lowercased() == "https" && url.host?.isEmpty == false ? url : nil
        }
        return Banner(
            id: id,
            title: title,
            subtitle: subtitle,
            webViewURL: webViewURL,
            thumbnailURL: thumbnailURL,
            createdAt: createdAt
        )
    }
}
