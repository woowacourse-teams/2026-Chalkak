import Foundation

nonisolated struct Banner: Identifiable, Equatable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let webViewURL: URL
    let thumbnailURL: URL?
    let createdAt: String
}

nonisolated struct BannerPage: Equatable, Sendable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool
    let banners: [Banner]
}
