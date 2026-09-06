import Foundation

struct DisplayAPIConfiguration: Sendable {
    let baseURL: URL

    static let development = DisplayAPIConfiguration(
        baseURL: URL(string: "https://chalkak-dev.pysun.kr/api/v1/")!
    )
}

struct DisplayAPIClient: Sendable {
    private let configuration: DisplayAPIConfiguration
    private let session: URLSession
    private let decoder: JSONDecoder

    init(
        configuration: DisplayAPIConfiguration = .development,
        session: URLSession = .shared
    ) {
        self.configuration = configuration
        self.session = session
        self.decoder = JSONDecoder()
    }

    func fetchDisplay(date: Date, sort: DisplaySort) async throws -> DisplayContent {
        let requestedDate = Self.apiDateFormatter.string(from: date)
        let topic: DisplayTopicResponse = try await request(
            path: "topics",
            queryItems: [URLQueryItem(name: "date", value: requestedDate)]
        )
        guard let canonicalDate = Self.apiDateFormatter.date(from: topic.topicDate) else {
            throw DisplayAPIError.invalidResponse
        }

        let page = try await fetchPage(
            DisplayPageRequest(
                topicDate: canonicalDate,
                sort: sort,
                page: 1,
                randomSeed: nil
            )
        )
        return DisplayContent(topicDate: canonicalDate, topic: topic.title, page: page)
    }

    func fetchPage(_ pageRequest: DisplayPageRequest) async throws -> DisplayPage {
        guard pageRequest.page >= 1 else {
            throw DisplayAPIError.invalidResponse
        }
        if pageRequest.sort == .random,
           pageRequest.page > 1,
           pageRequest.randomSeed?.isEmpty != false {
            throw DisplayAPIError.invalidResponse
        }

        var queryItems = [
            URLQueryItem(
                name: "topicDate",
                value: Self.apiDateFormatter.string(from: pageRequest.topicDate)
            ),
            URLQueryItem(name: "sort", value: pageRequest.sort.apiValue),
            URLQueryItem(name: "page", value: String(pageRequest.page)),
            URLQueryItem(name: "pageSize", value: "20")
        ]
        if pageRequest.sort == .random,
           let randomSeed = pageRequest.randomSeed,
           !randomSeed.isEmpty {
            queryItems.append(URLQueryItem(name: "randomSeed", value: randomSeed))
        }

        let response: DisplayPostPageResponse = try await request(
            path: "posts",
            queryItems: queryItems
        )
        let effectiveSeed = pageRequest.sort == .random
            ? response.randomSeed ?? pageRequest.randomSeed
            : nil
        if pageRequest.sort == .random,
           response.hasNext,
           effectiveSeed?.isEmpty != false {
            throw DisplayAPIError.invalidResponse
        }

        return DisplayPage(
            photos: try response.posts.map { try $0.toDisplayPhoto() },
            currentPage: response.currentPage,
            hasNext: response.hasNext,
            randomSeed: effectiveSeed
        )
    }

    func fetchPage(
        topicDate: Date,
        sort: DisplaySort,
        page: Int,
        randomSeed: String?
    ) async throws -> DisplayPage {
        try await fetchPage(
            DisplayPageRequest(
                topicDate: topicDate,
                sort: sort,
                page: page,
                randomSeed: randomSeed
            )
        )
    }

    private func request<Response: Decodable>(
        path: String,
        queryItems: [URLQueryItem]
    ) async throws -> Response {
        guard var components = URLComponents(
            url: configuration.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw DisplayAPIError.invalidResponse
        }
        components.queryItems = queryItems
        guard let url = components.url, url.scheme == "https" else {
            throw DisplayAPIError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else {
                throw DisplayAPIError.invalidResponse
            }
            guard (200..<300).contains(response.statusCode) else {
                throw DisplayAPIError.http(response.statusCode)
            }
            guard !data.isEmpty else {
                throw DisplayAPIError.invalidResponse
            }
            do {
                return try decoder.decode(Response.self, from: data)
            } catch {
                throw DisplayAPIError.invalidResponse
            }
        } catch let error as DisplayAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw DisplayAPIError.network
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

enum DisplayAPIError: Error, Equatable, Sendable {
    case network
    case invalidResponse
    case http(Int)

    var displayError: DisplayError {
        switch self {
        case .network:
            .network
        case .invalidResponse:
            .invalidResponse
        case let .http(statusCode):
            switch statusCode {
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

private extension DisplaySort {
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
