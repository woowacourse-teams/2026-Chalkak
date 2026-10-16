import SwiftUI
import UIKit

/// "이미지로 저장"에서 사진 앱에 저장하는 대상 스냅샷.
/// Android가 캡처하는 영역(상단바 + 요일 헤더 + 그리드)과 구성을 맞춘다.
/// 좌우 패딩(20)은 화면과 동일하게 이 뷰가 직접 적용한다.
struct RecordCalendarSnapshot: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let posts: [RecordPost]
    let canGoPrevious: Bool
    let canGoNext: Bool
    let width: CGFloat
    let thumbnailImages: [RecordPost.ID: UIImage]?

    init(
        month: RecordMonth,
        posts: [RecordPost],
        canGoPrevious: Bool,
        canGoNext: Bool,
        width: CGFloat,
        thumbnailImages: [RecordPost.ID: UIImage]? = nil
    ) {
        self.month = month
        self.posts = posts
        self.canGoPrevious = canGoPrevious
        self.canGoNext = canGoNext
        self.width = width
        self.thumbnailImages = thumbnailImages
    }

    var body: some View {
        VStack(spacing: 0) {
            RecordTopBar(
                month: month,
                canGoPrevious: canGoPrevious,
                canGoNext: canGoNext,
                onPrevious: {},
                onNext: {}
            )

            Spacer().frame(height: Metrics.topBarToWeekday)

            RecordWeekdayHeader()
                .padding(.horizontal, Metrics.horizontalPadding)

            Spacer().frame(height: Metrics.weekdayToGrid)

            RecordCalendarGrid(
                month: month,
                posts: posts,
                onDateClick: { _ in },
                thumbnailImages: thumbnailImages
            )
            .padding(.horizontal, Metrics.horizontalPadding)

            Spacer().frame(height: Metrics.bottomPadding)
        }
        .frame(width: width)
        .background(theme.colors.background)
    }

    private enum Metrics {
        static let horizontalPadding: CGFloat = 20
        static let topBarToWeekday: CGFloat = 8
        static let weekdayToGrid: CGFloat = 14
        static let bottomPadding: CGFloat = 20
    }
}
