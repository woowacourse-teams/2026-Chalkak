import Foundation

/// Preview 전용 데이터. 제품 도메인 모델을 오염시키지 않도록 이 파일에만 둔다.
enum RecordPreviewData {
    static let month = RecordMonth(year: 2026, month: 8)

    static let posts: [RecordPost] = [
        RecordPost(
            postId: "preview-2",
            topicDate: month.date(day: 2),
            thumbnailImageSource: .asset("preview_photo"),
            status: .approved
        ),
        RecordPost(
            postId: "preview-9",
            topicDate: month.date(day: 9),
            thumbnailImageSource: .asset("preview_sunset"),
            status: .pending
        ),
        RecordPost(
            postId: "preview-17",
            topicDate: month.date(day: 17),
            thumbnailImageSource: .asset("preview_signature"),
            status: .approved
        )
    ]

    static var loadedState: RecordViewState {
        RecordViewState(
            contentStatus: .loaded,
            month: month,
            latestMonth: month,
            posts: posts,
            selectedDate: posts.first?.topicDate
        )
    }

    static var errorState: RecordViewState {
        RecordViewState(
            contentStatus: .error(.generic),
            month: month,
            latestMonth: month
        )
    }

    static var loginRequiredState: RecordViewState {
        RecordViewState(
            contentStatus: .error(.unauthorized),
            month: month,
            latestMonth: month
        )
    }
}
