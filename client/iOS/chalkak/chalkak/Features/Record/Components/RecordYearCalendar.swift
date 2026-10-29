import SwiftUI

/// 한 해를 한 화면에 보는 연간 달력.
/// - 달마다 날짜를 작은 칸으로 그리고, 기록이 있는 날은 그 사진의 대표색으로 칠한다.
/// - 달을 누르면 그 달의 월간 화면으로 들어간다. 아직 오지 않은 달은 누를 수 없다.
/// - 다른 해는 위의 화살표로 옮겨 간다.
struct RecordYearCalendar: View {
    @Environment(\.chalkakTheme) private var theme
    let year: Int
    let latestMonth: RecordMonth
    let canGoPreviousYear: Bool
    let canGoNextYear: Bool
    let postsByMonth: [RecordMonth: [RecordPost]]
    let photoColors: [RecordPost.ID: RecordPhotoColor]
    let namespace: Namespace.ID
    var bottomBarCompact: Binding<Bool> = .constant(false)
    let onPreviousYear: () -> Void
    let onNextYear: () -> Void
    let onSelectMonth: (RecordMonth) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: theme.spacing.xl) {
                header

                LazyVGrid(
                    columns: Array(repeating: GridItem(.flexible(), spacing: Metrics.monthSpacing), count: Metrics.columnCount),
                    alignment: .leading,
                    spacing: Metrics.monthRowSpacing
                ) {
                    ForEach(1...12, id: \.self) { number in
                        monthCell(RecordMonth(year: year, month: number))
                    }
                }
            }
            .padding(.horizontal, Metrics.horizontalPadding)
            .padding(.top, theme.spacing.sm)
            .padding(.bottom, theme.spacing.xxl)
        }
        .chalkakBottomBarScrollBehavior(isCompact: bottomBarCompact)
    }

    private var header: some View {
        HStack(spacing: theme.spacing.none) {
            Text(verbatim: "\(year)")
                .font(theme.typography.title1)
                .monospacedDigit()
                .foregroundStyle(theme.colors.textPrimary)
                .contentTransition(.numericText())
                .accessibilityLabel("\(year)년")
                .accessibilityAddTraits(.isHeader)

            Spacer(minLength: 0)

            yearButton("이전 해", systemImage: "chevron.left", isEnabled: canGoPreviousYear, action: onPreviousYear)
            yearButton("다음 해", systemImage: "chevron.right", isEnabled: canGoNextYear, action: onNextYear)
        }
    }

    private func yearButton(
        _ title: String,
        systemImage: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(theme.typography.body)
                .foregroundStyle(isEnabled ? theme.colors.iconPrimary : theme.colors.textInactive)
                .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(title)
    }

    private func monthCell(_ month: RecordMonth) -> some View {
        let posts = postsByMonth[month] ?? []
        let isAvailable = month <= latestMonth

        return Button {
            onSelectMonth(month)
        } label: {
            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(verbatim: "\(month.month)월")
                    .font(theme.typography.headline)
                    .foregroundStyle(theme.colors.textPrimary)

                RecordYearMonthDays(month: month, posts: posts, photoColors: photoColors)
                    .matchedGeometryEffect(id: RecordMonthTransition.id(month), in: namespace)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isAvailable)
        .opacity(isAvailable ? 1 : Metrics.unavailableOpacity)
        .accessibilityLabel("\(month.month)월, 기록 \(posts.count)개")
    }

    private enum Metrics {
        static let horizontalPadding: CGFloat = 20
        static let columnCount = 3
        static let monthSpacing: CGFloat = 18
        static let monthRowSpacing: CGFloat = 28
        static let touchSize: CGFloat = 44
        static let unavailableOpacity: Double = 0.35
    }
}

/// 한 달의 날짜 칸. 일요일부터 한 줄에 7칸.
private struct RecordYearMonthDays: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let posts: [RecordPost]
    let photoColors: [RecordPost.ID: RecordPhotoColor]

    var body: some View {
        let postsByDay = Dictionary(
            posts.map { (RecordMonth.calendar.component(.day, from: $0.topicDate), $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let cells: [Int?] = Array(repeating: nil, count: month.leadingEmptyDayCount) + (1...month.lengthOfMonth).map { $0 }
        let rows = stride(from: 0, to: cells.count, by: 7).map { start in
            (0..<7).map { offset in cells.indices.contains(start + offset) ? cells[start + offset] : nil }
        }

        Grid(horizontalSpacing: Metrics.spacing, verticalSpacing: Metrics.spacing) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, day in
                        if let day {
                            RoundedRectangle(cornerRadius: Metrics.cornerRadius)
                                .fill(fill(for: postsByDay[day]))
                                .aspectRatio(1, contentMode: .fit)
                        } else {
                            Color.clear
                                .aspectRatio(1, contentMode: .fit)
                        }
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// 기록이 없는 날은 빈 칸 색, 색을 아직 구하지 못한 사진은 조금 진한 자리표시 색.
    private func fill(for post: RecordPost?) -> Color {
        guard let post else { return theme.colors.calendarCell }
        guard let color = photoColors[post.postId] else { return theme.colors.calendarCellBorder }
        return Color(red: color.red, green: color.green, blue: color.blue)
    }

    private enum Metrics {
        static let spacing: CGFloat = 2.5
        static let cornerRadius: CGFloat = 2.5
    }
}

/// 연간 달력의 작은 달과 월간 화면의 달력이 이어지는 전환의 식별자.
enum RecordMonthTransition {
    static func id(_ month: RecordMonth) -> String {
        "record-month-\(month.year)-\(month.month)"
    }
}
