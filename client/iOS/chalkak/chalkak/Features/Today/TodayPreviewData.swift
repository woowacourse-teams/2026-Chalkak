import Foundation

enum TodayPreviewData {
    static let contentState = TodayViewState(
        contentStatus: .content,
        topicDate: Calendar(identifier: .gregorian).date(
            from: DateComponents(year: 2026, month: 8, day: 3)
        ),
        topic: "하늘하늘하늘",
        photos: [
            TodayPhoto(
                id: "preview-1",
                imageSource: .asset("preview_photo"),
                signatureSource: .asset("preview_signature"),
                contentDescription: "노을이 진 하늘과 전신주",
                title: "안녕하세요 찰캌입니다.",
                likeCount: 24
            ),
            TodayPhoto(
                id: "preview-2",
                imageSource: .asset("preview_photo"),
                signatureSource: .asset("preview_signature"),
                contentDescription: "두 번째 사진",
                title: nil,
                likeCount: 12
            )
        ],
        selectedSort: .latest,
        likedPhotoIDs: ["preview-1"],
        currentPage: 0,
        hasNext: true,
        areLikesEnabled: true
    )

    static let loadingState = TodayViewState(contentStatus: .loading)

    static let errorState = TodayViewState(
        contentStatus: .error(.network),
        areLikesEnabled: false
    )
}
