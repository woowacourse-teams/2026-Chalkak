import SwiftUI
import UIKit

/// 월 단위 사진 달력 그리드.
/// Android `RecordCalendarGrid`와 레이아웃을 맞춘다.
/// - 7열, 셀 간격 6, 셀은 1:1 정사각, 모서리 7.
/// - 사진이 있는 날짜만 셀을 그리고, 없는 칸은 빈 공간으로 둔다.
/// - 셀 사이 간격 중앙에 1pt 구분선을 그린다(color = calendarCellBorder α0.6).
/// 화면 좌우 패딩은 호출부가 관리한다.
struct RecordCalendarGrid: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let posts: [RecordPost]
    let onDateClick: (Date) -> Void
    let thumbnailImages: [RecordPost.ID: UIImage]?

    init(
        month: RecordMonth,
        posts: [RecordPost],
        onDateClick: @escaping (Date) -> Void,
        thumbnailImages: [RecordPost.ID: UIImage]? = nil
    ) {
        self.month = month
        self.posts = posts
        self.onDateClick = onDateClick
        self.thumbnailImages = thumbnailImages
    }

    private var postsByDate: [Date: RecordPost] {
        Dictionary(posts.map { ($0.topicDate, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// 앞쪽 빈칸 + 각 날짜 + 마지막 주를 채우는 뒤쪽 빈칸.
    private var weeks: [[Date?]] {
        var days: [Date?] = Array(repeating: nil, count: month.leadingEmptyDayCount)
        days.append(contentsOf: (1...month.lengthOfMonth).map { month.date(day: $0) })
        let trailing = (Metrics.columns - days.count % Metrics.columns) % Metrics.columns
        days.append(contentsOf: Array(repeating: nil, count: trailing))
        return stride(from: 0, to: days.count, by: Metrics.columns).map {
            Array(days[$0..<min($0 + Metrics.columns, days.count)])
        }
    }

    var body: some View {
        let weeks = weeks
        VStack(spacing: Metrics.itemGap) {
            ForEach(Array(weeks.enumerated()), id: \.offset) { _, week in
                HStack(spacing: Metrics.itemGap) {
                    ForEach(Array(week.enumerated()), id: \.offset) { _, date in
                        cell(for: date)
                    }
                }
            }
        }
        .background {
            RecordCalendarDividers(
                columns: Metrics.columns,
                rows: weeks.count,
                gap: Metrics.itemGap,
                lineWidth: Metrics.dividerWidth,
                color: theme.colors.calendarCellBorder.opacity(Metrics.dividerOpacity)
            )
        }
    }

    /// 정사각 셀. 크기는 `Color.clear`가 정하고(1:1), 사진은 overlay로 얹어 이미지 비율이
    /// 셀 크기에 영향을 주지 않게 한다(Display 그리드와 동일한 방식).
    private func cell(for date: Date?) -> some View {
        let shape = RoundedRectangle(cornerRadius: theme.shapes.small)

        return Color.clear
            .aspectRatio(Metrics.cellAspectRatio, contentMode: .fit)
            .overlay {
                if let date, let post = postsByDate[date] {
                    photoCell(post)
                }
            }
            // 사진 레이어가 아니라 정사각 앵커를 기준으로 잘라야 고유 비율이
            // 셀의 경계를 넘어 그려지지 않는다.
            .clipShape(shape)
            .frame(maxWidth: .infinity)
    }

    private func photoCell(_ post: RecordPost) -> some View {
        photoImage(for: post)
        // Android ContentScale.Crop — 정사각 셀을 채우고 넘치는 부분은 잘라낸다.
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.calendarCell)
        .onTapGesture { onDateClick(post.topicDate) }
        .accessibilityElement()
        .accessibilityLabel("\(Self.dateFormatter.string(from: post.topicDate)) 사진")
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder
    private func photoImage(for post: RecordPost) -> some View {
        if let image = thumbnailImages?[post.id] {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if thumbnailImages != nil, case .remote = post.thumbnailImageSource {
            // 스냅샷은 원격 이미지 로드가 끝난 뒤 동기적으로 렌더링한다.
            // 실패한 원격 이미지는 AsyncImage 대신 고정 플레이스홀더를 사용한다.
            ChalkakImage(
                source: .system("photo"),
                contentDescription: nil,
                contentMode: .fit
            )
        } else {
            ChalkakImage(
                source: post.thumbnailImageSource,
                contentDescription: nil,
                contentMode: .fill
            )
        }
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy년 M월 d일"
        return formatter
    }()
}

/// 셀 사이 간격 중앙에 그려지는 격자 구분선.
/// Android `drawBehind`의 좌표 공식을 그대로 옮긴다(간격 중앙에 선).
private struct RecordCalendarDividers: View {
    let columns: Int
    let rows: Int
    let gap: CGFloat
    let lineWidth: CGFloat
    let color: Color

    var body: some View {
        Canvas { context, size in
            let itemWidth = (size.width - gap * CGFloat(columns - 1)) / CGFloat(columns)
            for column in 1..<max(columns, 2) {
                let x = itemWidth * CGFloat(column) + gap * (CGFloat(column) - 0.5)
                var path = Path()
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: size.height))
                context.stroke(path, with: .color(color), lineWidth: lineWidth)
            }

            guard rows > 1 else { return }
            let rowHeight = (size.height - gap * CGFloat(rows - 1)) / CGFloat(rows)
            for row in 1..<rows {
                let y = rowHeight * CGFloat(row) + gap * (CGFloat(row) - 0.5)
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(path, with: .color(color), lineWidth: lineWidth)
            }
        }
        .accessibilityHidden(true)
    }
}

private enum Metrics {
    static let columns = 7
    static let itemGap: CGFloat = 6
    static let dividerWidth: CGFloat = 1
    static let dividerOpacity: CGFloat = 0.6
    static let cellAspectRatio: CGFloat = 1
}

#Preview("Record Calendar Grid", traits: .sizeThatFitsLayout) {
    RecordCalendarGrid(
        month: RecordPreviewData.month,
        posts: RecordPreviewData.posts,
        onDateClick: { _ in }
    )
    .padding(.horizontal, 20)
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
