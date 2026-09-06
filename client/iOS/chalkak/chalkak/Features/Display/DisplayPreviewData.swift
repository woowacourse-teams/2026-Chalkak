import Foundation

enum DisplayPreviewData {
    static let latestState = DisplayViewState(
        contentStatus: .latest,
        selectedDate: date(2026, 9, 2),
        latestDate: date(2026, 9, 2),
        earliestDate: date(2026, 8, 1),
        topic: "반짝이는 순간",
        selectedSort: .latest,
        photos: photos,
        currentPage: 1,
        hasNext: true
    )

    static let archiveState = DisplayViewState(
        contentStatus: .archive,
        selectedDate: date(2026, 8, 20),
        latestDate: date(2026, 9, 2),
        earliestDate: date(2026, 8, 1),
        topic: "여름의 끝",
        selectedSort: .popular,
        photos: photos,
        featuredPhotos: Array(photos.prefix(3)),
        featuredPage: 0,
        currentPage: 1,
        hasNext: true
    )

    static let loadingState = DisplayViewState(
        contentStatus: .loading,
        selectedDate: date(2026, 9, 2),
        latestDate: date(2026, 9, 2)
    )

    static let errorState = DisplayViewState(
        contentStatus: .error(.network),
        selectedDate: date(2026, 9, 2),
        latestDate: date(2026, 9, 2)
    )

    private static let photos: [DisplayPhoto] = (1...6).map { index in
        DisplayPhoto(
            id: "preview-\(index)",
            originalImageSource: .asset("preview_photo"),
            thumbnailImageSource: .asset("preview_photo"),
            signatureOriginalImageSource: .asset("preview_signature"),
            signatureThumbnailImageSource: .asset("preview_signature"),
            contentDescription: "미리보기 전시 사진 \(index)",
            title: index.isMultiple(of: 2) ? nil : "노을 \(index)",
            likeCount: index * 7
        )
    }

    private static func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}
