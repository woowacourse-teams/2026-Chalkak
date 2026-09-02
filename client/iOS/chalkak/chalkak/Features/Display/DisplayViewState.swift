import Foundation

enum DisplaySort: CaseIterable, Equatable, Sendable {
    case latest
    case popular
    case random
}

enum DisplayContentStatus: Equatable, Sendable {
    case loading
    case latest
    case archive
    case error(DisplayError)
}

enum DisplayError: Error, Equatable, Sendable {
    case topicNotFound
    case network
    case invalidResponse
    case client
    case server
    case generic

    var message: String {
        switch self {
        case .topicNotFound:
            "해당 날짜의 전시가 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .invalidResponse, .client, .server, .generic:
            "전시를 불러오지 못했어요"
        }
    }
}

struct DisplayPhoto: Identifiable, Equatable, Sendable {
    let id: String
    let originalImageSource: ChalkakImageSource
    let thumbnailImageSource: ChalkakImageSource
    let signatureOriginalImageSource: ChalkakImageSource
    let signatureThumbnailImageSource: ChalkakImageSource
    let contentDescription: String
    let title: String?
    let likeCount: Int
}

struct DisplayPage: Equatable, Sendable {
    let photos: [DisplayPhoto]
    let currentPage: Int
    let hasNext: Bool
    let randomSeed: String?
}

struct DisplayContent: Equatable, Sendable {
    let topicDate: Date
    let topic: String
    let page: DisplayPage
}

struct DisplayPageRequest: Equatable, Sendable {
    let topicDate: Date
    let sort: DisplaySort
    let page: Int
    let randomSeed: String?
}

struct DisplayViewState: Equatable, Sendable {
    var contentStatus: DisplayContentStatus = .loading
    var selectedDate: Date?
    var latestDate: Date?
    var earliestDate: Date?
    var topic = ""
    var selectedSort: DisplaySort = .latest
    var photos: [DisplayPhoto] = []
    var featuredPhotos: [DisplayPhoto] = []
    var featuredPage = 0
    var currentPage = 0
    var hasNext = false
    var randomSeed: String?
    var isLoadingNext = false
    var transientError: DisplayError?

    var canGoPrevious: Bool {
        guard contentStatus != .loading, let selectedDate else { return false }
        return earliestDate.map { selectedDate > $0 } ?? true
    }

    var canGoNext: Bool {
        guard contentStatus != .loading,
              let selectedDate,
              let latestDate
        else { return false }
        return selectedDate < latestDate
    }
}

enum DisplayEvent: Equatable, Sendable {
    case showFailure(DisplayError)
}
