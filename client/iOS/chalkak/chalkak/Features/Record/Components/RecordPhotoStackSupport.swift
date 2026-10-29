import SwiftUI
import UIKit

/// 월간 상단 사진 묶음의 모양. 화면 오른쪽 위 전환 버튼으로 바꿔 보며 A/B로 비교한다.
enum RecordPhotoStackStyle: String, CaseIterable, Identifiable {
    /// 한 줄 기둥 + 눈금자(시안 2번).
    case pillars
    /// 한 달이 한 권인 종이책(시안 4번).
    case book

    var id: String { rawValue }

    var title: String {
        switch self {
        case .pillars: "기둥과 눈금자"
        case .book: "월간 책자"
        }
    }

    var systemImage: String {
        switch self {
        case .pillars: "photo.stack"
        case .book: "book"
        }
    }

    var toggled: RecordPhotoStackStyle { self == .pillars ? .book : .pillars }
}

/// 묶음 위 한 장. 기록이 있는 날은 사진, 없는 날은 빈 프레임이다.
struct RecordPhotoStackEntry: Identifiable, Equatable {
    let date: Date
    let post: RecordPost?

    var id: String { post?.id ?? "empty-\(date.timeIntervalSince1970)" }
    var day: Int { RecordMonth.calendar.component(.day, from: date) }
}

/// 묶음 안의 순서. 화면 크기와 무관한 값만 다룬다.
/// 두 모양 모두 위치(`position`)는 보고 있는 날의 순서다(가장 최근 날이 0).
struct RecordPhotoStackOrder {
    /// 그 달 1일부터 지난 날까지, 가장 최근 날이 먼저 온다.
    let entries: [RecordPhotoStackEntry]

    private static let rubberBandRatio: CGFloat = 0.35

    /// - Parameter elapsedDays: 빈 프레임까지 채울 마지막 날. 기본값은 오늘(지난 달은 말일)이다.
    init(month: RecordMonth, posts: [RecordPost], elapsedDays: Int? = nil) {
        let postsByDay = Dictionary(
            posts.map { (RecordMonth.calendar.component(.day, from: $0.topicDate), $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let lastDay = min(
            month.lengthOfMonth,
            max(elapsedDays ?? month.elapsedDayCount(), postsByDay.keys.max() ?? 0)
        )
        entries = stride(from: lastDay, through: 1, by: -1).map { day in
            RecordPhotoStackEntry(date: month.date(day: day), post: postsByDay[day])
        }
    }

    /// 범위를 넘어 끌면 고무줄처럼 덜 움직인다.
    static func rubberBanded(_ raw: CGFloat, in range: ClosedRange<CGFloat>) -> CGFloat {
        if raw < range.lowerBound { return range.lowerBound + (raw - range.lowerBound) * rubberBandRatio }
        if raw > range.upperBound { return range.upperBound + (raw - range.upperBound) * rubberBandRatio }
        return raw
    }

    /// 묶음이 지금 보여 주는 장. 책은 표지·속표지를 볼 때 위치가 마지막 장을 넘어가므로 1일로 맞춘다.
    /// 달력은 이 장이 속한 주를 띄워 보인다.
    func focusedIndex(at position: CGFloat) -> Int? {
        guard !entries.isEmpty else { return nil }
        return min(entries.count - 1, max(0, Int(position.rounded())))
    }

    /// 그 장을 보여 주는 위치. `focusedIndex(at:)`의 반대다.
    func position(focusing index: Int) -> CGFloat {
        CGFloat(min(max(0, entries.count - 1), max(0, index)))
    }

    /// 묶음 안에서 그 사진이 몇 번째인지(가장 최근 날이 0).
    func index(of post: RecordPost) -> Int? {
        entries.firstIndex { $0.post?.id == post.id }
    }

    /// 그 날짜가 몇 번째인지(가장 최근 날이 0).
    func index(of date: Date) -> Int? {
        entries.firstIndex { RecordMonth.calendar.isDate($0.date, inSameDayAs: date) }
    }
}

/// 사진 비율(너비 / 높이). 아직 내려받지 못한 사진은 4:5로 자리를 잡는다.
enum RecordPhotoAspect {
    static let placeholder: CGFloat = 4.0 / 5.0
    /// 빈 날 프레임의 비율(3:4).
    static let emptyFrame: CGFloat = 3.0 / 4.0

    static func widthRatio(of image: UIImage?) -> CGFloat {
        guard let image, image.size.width > 0, image.size.height > 0 else { return placeholder }
        return image.size.width / image.size.height
    }
}

/// 내려받은 썸네일 또는 자리표시 배경.
struct RecordPhotoImage: View {
    @Environment(\.chalkakTheme) private var theme
    let image: UIImage?
    var contentMode: ContentMode = .fill

    var body: some View {
        if let image {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        } else {
            theme.colors.calendarCell
        }
    }
}

/// 기록이 없는 날의 자리. 바탕 위에 기본 글자색을 55% 겹친 불투명 차콜 면으로, 뒤 사진이 비치지 않는다.
/// 빈 날끼리 겹쳐도 갈라져 보이도록 옅은 흰 안쪽 테두리를 두른다.
struct RecordEmptyFrame: View {
    @Environment(\.chalkakTheme) private var theme
    /// nil이면 날짜를 적지 않는다.
    var day: Int?
    var labelAlignment: Alignment = .topTrailing

    var body: some View {
        ZStack(alignment: labelAlignment) {
            theme.colors.background
            theme.colors.textPrimary.opacity(Metrics.inkOpacity)
            if let day {
                Text(verbatim: "\(day)")
                    .font(theme.typography.caption)
                    .monospacedDigit()
                    .foregroundStyle(theme.colors.textOnImage.opacity(Metrics.labelOpacity))
                    .padding(Metrics.labelPadding)
            }
        }
        .overlay {
            Rectangle()
                .strokeBorder(theme.colors.textOnImage.opacity(Metrics.edgeOpacity), lineWidth: Metrics.edgeWidth)
        }
        .accessibilityHidden(true)
    }

    private enum Metrics {
        static let inkOpacity: Double = 0.55
        static let labelOpacity: Double = 0.72
        static let edgeOpacity: Double = 0.22
        static let edgeWidth: CGFloat = 1
        static let labelPadding: CGFloat = 6
    }
}

/// 묶음 왼쪽 위의 월과 연도. 뒤로 가기 버튼 자리에 놓이며, 누르면 연간 달력으로 나가 다른 달을 고른다.
/// 달은 크게, 연도는 작게 한 줄로 적는다. 예: "10월 2026".
struct RecordStackMonthButton: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .firstTextBaseline, spacing: theme.spacing.xs) {
                Text(verbatim: "\(month.month)월")
                    .font(theme.typography.title2)
                    .foregroundStyle(theme.colors.textPrimary)

                Text(verbatim: "\(month.year)")
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.textMuted)
            }
            .padding(.horizontal, Metrics.horizontalPadding)
            .frame(minHeight: ChalkakNavigationButton.diameter)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(month.formatted)
        .accessibilityHint("연간 달력에서 다른 달을 고릅니다")
    }

    private enum Metrics {
        /// 바깥 여백(12pt)과 더해 달력 왼쪽 끝(20pt)에 글자를 맞춘다.
        static let horizontalPadding: CGFloat = 8
    }
}

/// 뒤로 가기 버튼과 같은 모양의 아이콘 버튼.
struct RecordGlassIconButton: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.isEnabled) private var isEnabled
    private static let disabledOpacity: Double = 0.6
    let title: String
    let systemImage: String
    /// 책 양 끝 화살표처럼 작은 자리에 둘 때 줄인다. 누를 자리는 44pt 아래로 줄이지 않는다.
    var diameter: CGFloat = ChalkakNavigationButton.diameter
    var controlSize: ControlSize = .large
    let action: () -> Void

    var body: some View {
        Button(title, systemImage: systemImage, action: action)
            .labelStyle(.iconOnly)
            // 유리 버튼은 꺼져도 흐려지지 않아서, 꺼지면 아이콘을 비활성 색으로 바꿔 누를 수 없음을 보인다.
            .foregroundStyle(isEnabled ? theme.colors.iconPrimary : theme.colors.textInactive)
            .tint(isEnabled ? theme.colors.iconPrimary : theme.colors.textInactive)
            .opacity(isEnabled ? 1 : Self.disabledOpacity)
            .controlSize(controlSize)
            .modifier(ChalkakNavigationButtonStyle())
            .buttonBorderShape(.circle)
            .frame(width: diameter, height: diameter)
            .accessibilityLabel(title)
    }
}

enum RecordStackMetrics {
    /// 묶음 영역의 높이. 두 모양이 같은 높이를 쓴다. 책 아래 그림자가 잘리지 않을 만큼 여유를 둔다.
    static let height: CGFloat = 300
    /// 묶음 내용(기둥, 책 표지)이 시작하는 높이. 위 버튼 줄(위 여백 8 + 48pt) 아래로 28pt를 띄운다.
    /// 묶음이 커지거나 줄어도, 기둥이든 책자든 이 간격은 같다.
    static let contentTop: CGFloat = 84
    static let dragMinimumDistance: CGFloat = 6
}

enum RecordStackFormat {
    static let monthDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일"
        return formatter
    }()

    static let monthDayWeekday: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 E"
        return formatter
    }()

    static let weekday: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "EEEE"
        return formatter
    }()
}
