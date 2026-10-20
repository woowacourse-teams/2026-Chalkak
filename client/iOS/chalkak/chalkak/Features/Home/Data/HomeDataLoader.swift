import Foundation

/// Home composes existing public feed and private record APIs without owning navigation.
struct HomeDataLoader: Sendable {
    var today: @Sendable (Date) async throws -> DisplayContent
    var ranking: @Sendable (Date) async throws -> [DisplayPhoto]
    var calendar: @Sendable (RecordMonth) async throws -> RecordCalendar
    var detail: @Sendable (String) async throws -> FeedContent
    var banners: @Sendable () async throws -> BannerPage
    var postStatus: @Sendable () async throws -> PhotoUploadTodayPostStatus

    static func live(baseURL: URL) -> Self {
        let token: @Sendable () async -> String? = { await MainActor.run { KeychainSessionStore.accessToken() } }
        let display = DisplayAPIClient(configuration: DisplayAPIConfiguration(baseURL: baseURL), accessTokenProvider: token)
        let record = RecordAPIClient(configuration: RecordAPIConfiguration(baseURL: baseURL), accessTokenProvider: token)
        let feed = FeedAPIClient(configuration: FeedAPIConfiguration(baseURL: baseURL), accessTokenProvider: token)
        let banner = BannerAPIClient(configuration: BannerAPIConfiguration(baseURL: baseURL))
        let upload = PhotoUploadAPIClient(configuration: PhotoUploadAPIConfiguration(baseURL: baseURL), accessTokenProvider: token)
        return Self(
            today: { try await display.fetchDisplay(date: $0, sort: .popular) },
            ranking: { date in
                try await display.fetchPage(topicDate: date, sort: .popular, page: 1, randomSeed: nil).photos
            },
            calendar: { try await record.fetchCalendar(month: $0) },
            detail: { try await feed.fetchPostDetail(postID: $0) },
            banners: { try await banner.fetchPage(page: 1, pageSize: 1) },
            postStatus: { try await upload.fetchTodayPostStatus() }
        )
    }
}
