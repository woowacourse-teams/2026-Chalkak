import CoreGraphics
import Foundation
import Testing
import UIKit
@testable import chalkak

@MainActor
@Suite(.serialized)
struct RecordWeekAndYearViewModelTests {
    private static let september = RecordMonth(year: 2026, month: 9)

    @Test("주제는 아직 모르는 날짜만 조회한다")
    func loadTopicTitlesFetchesOnlyMissingDates() async {
        let known = Self.september.date(day: 1)
        let missing = Self.september.date(day: 2)
        let recorder = RecordCallRecorder<Date>()
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: Self.september,
                latestMonth: Self.september,
                topicTitles: [known: "아는 주제"]
            ),
            monthProvider: { Self.september },
            topicHandler: { date in
                recorder.record(date)
                return "새 주제"
            }
        )

        await viewModel.loadTopicTitles(for: [known, missing])

        #expect(recorder.values == [missing])
        #expect(viewModel.viewState.topicTitles == [known: "아는 주제", missing: "새 주제"])
    }

    @Test("연간 달력은 그 해의 기록 있는 달만 불러오고 사진 색을 채운다")
    func loadYearFetchesRecordedMonthsAndColors() async {
        let july = RecordMonth(year: 2026, month: 7)
        let lastDecember = RecordMonth(year: 2025, month: 12)
        let recorder = RecordCallRecorder<RecordMonth>()
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: Self.september,
                latestMonth: Self.september,
                availableMonths: [Self.september, july, lastDecember]
            ),
            monthProvider: { Self.september },
            calendarHandler: { month in
                recorder.record(month)
                return .success(RecordCalendar(month: month, posts: [Self.post(id: "post-\(month.month)", day: 1, month: month)]))
            },
            photoColorHandler: { _ in RecordPhotoColor(red: 0.1, green: 0.2, blue: 0.3) }
        )

        await viewModel.loadYear(2026)

        #expect(Set(recorder.values) == [Self.september, july])
        #expect(Set(viewModel.viewState.yearPosts.keys) == [Self.september, july])
        #expect(viewModel.viewState.photoColors.count == 2)
        #expect(viewModel.viewState.earliestYear == 2025)
    }

    @Test("연간 달력에서 고른 달은 이미 불러온 게시물로 바로 보여준다")
    func showMonthUsesYearCache() {
        let july = RecordMonth(year: 2026, month: 7)
        let cached = Self.post(id: "july-post", day: 3, month: july)
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: Self.september,
                latestMonth: Self.september,
                yearPosts: [july: [cached]]
            ),
            monthProvider: { Self.september }
        )

        viewModel.showMonth(july)

        #expect(viewModel.viewState.month == july)
        #expect(viewModel.viewState.posts == [cached])
        #expect(viewModel.viewState.contentStatus == .loaded)

        viewModel.showMonth(RecordMonth(year: 2026, month: 6))
        #expect(viewModel.viewState.contentStatus == .loading)
        #expect(viewModel.viewState.posts.isEmpty)
    }

    static func post(id: String, day: Int, month: RecordMonth) -> RecordPost {
        RecordPost(
            postId: id,
            topicDate: month.date(day: day),
            thumbnailImageSource: .remote(URL(string: "https://example.com/\(id).webp")),
            status: .approved
        )
    }
}

@MainActor
private final class RecordCallRecorder<Value> {
    private(set) var values: [Value] = []

    func record(_ value: Value) {
        values.append(value)
    }
}

@MainActor
struct RecordPhotoStackTests {
    // 2026년 9월 1일은 화요일이라 첫 주는 1~5일, 둘째 주는 6~12일이다.
    private static let month = RecordMonth(year: 2026, month: 9)

    private static func order(elapsedDays: Int = 8, postDays: [Int] = [1, 6, 7]) -> RecordPhotoStackOrder {
        RecordPhotoStackOrder(
            month: month,
            posts: postDays.map { RecordWeekAndYearViewModelTests.post(id: "\($0)", day: $0, month: month) },
            elapsedDays: elapsedDays
        )
    }

    @Test("주 순서는 일요일 시작 달력을 따른다")
    func weekIndexFollowsSundayStartCalendar() {
        #expect(Self.month.weekIndex(of: Self.month.date(day: 5)) == 0)
        #expect(Self.month.weekIndex(of: Self.month.date(day: 6)) == 1)
        #expect(Self.month.weekIndex(of: Self.month.date(day: 13)) == 2)
        #expect(Self.month.weekTitle(1) == "9월 둘째 주")
    }

    @Test("지난 날 수는 지난 달은 말일, 이번 달은 오늘, 오지 않은 달은 0이다")
    func elapsedDayCountStopsAtToday() {
        let now = Self.month.date(day: 8)
        #expect(Self.month.elapsedDayCount(now: now) == 8)
        #expect(RecordMonth(year: 2026, month: 8).elapsedDayCount(now: now) == 31)
        #expect(RecordMonth(year: 2026, month: 10).elapsedDayCount(now: now) == 0)
    }

    @Test("묶음은 최근 날부터 놓고 기록 없는 날도 빈 자리로 채운다")
    func orderPutsLatestFirstWithEmptyDays() {
        let order = Self.order()

        #expect(order.entries.map(\.day) == [8, 7, 6, 5, 4, 3, 2, 1])
        #expect(order.entries.map { $0.post?.postId } == [nil, "7", "6", nil, nil, nil, nil, "1"])
        #expect(order.index(of: Self.month.date(day: 6)) == 2)
    }

    @Test("책의 표지·속표지처럼 마지막 장을 넘어간 위치는 1일로, 범위 밖은 끝으로 맞춘다")
    func focusedIndexClampsToEntries() {
        let order = Self.order()

        #expect(order.focusedIndex(at: 2.4) == 2)
        #expect(order.focusedIndex(at: 9) == 7)
        #expect(order.focusedIndex(at: -3) == 0)
        #expect(Self.order(elapsedDays: 0, postDays: []).focusedIndex(at: 0) == nil)
    }

    @Test("고른 장으로 옮긴 위치는 다시 그 장을 가리킨다")
    func positionFocusingRoundTrips() {
        let order = Self.order()

        for index in order.entries.indices {
            #expect(order.focusedIndex(at: order.position(focusing: index)) == index)
        }
    }
}

@MainActor
struct RecordPillarLayoutTests {
    private static let width: CGFloat = 393

    @Test("1일이 왼쪽 끝, 가장 최근 날이 오른쪽 끝에 선다")
    func firstDayStandsAtLeft() {
        let layout = RecordPillarLayout(entryCount: 13, position: 0, width: Self.width, previousShift: nil)

        // 순서 12가 1일, 0이 가장 최근 날이다.
        #expect(abs(layout.centers[12] - layout.widths[12] / 2 - 20) < 0.0001)
        #expect(layout.centers[12] < layout.centers[0])
        #expect(abs(layout.widths[0] - 150) < 0.0001)
    }

    @Test("한 달이 화면에 들어갈 수 있으면 가는 기둥을 좁혀 오늘까지 다 보인다")
    func shortMonthFitsOnScreen() {
        let layout = RecordPillarLayout(entryCount: 13, position: 0, width: Self.width, previousShift: nil)
        let thin = RecordPillarLayout.thinWidth(entryCount: 13, width: Self.width)

        #expect(thin >= 8 && thin <= 12)
        #expect(layout.centers[0] + layout.widths[0] / 2 <= Self.width - 20 + 0.0001)
    }

    @Test("끄는 동안 펼친 자리는 하루에 가는 기둥 한 칸씩 곧게 움직인다")
    func focusMovesOneStepPerDay() {
        let step = RecordPillarLayout.step(entryCount: 13, width: Self.width)
        var previous = RecordPillarLayout(entryCount: 13, position: 0, width: Self.width, previousShift: nil).focusX
        for tenth in 1...120 {
            let position = CGFloat(tenth) / 10
            let focus = RecordPillarLayout(entryCount: 13, position: position, width: Self.width, previousShift: nil).focusX
            // 순서가 커질수록(지난 날로 갈수록) 펼친 자리는 왼쪽으로만 간다.
            #expect(focus <= previous + 0.0001)
            previous = focus
        }
        let start = RecordPillarLayout(entryCount: 13, position: 3, width: Self.width, previousShift: nil).focusX
        let next = RecordPillarLayout(entryCount: 13, position: 4, width: Self.width, previousShift: nil).focusX
        #expect(abs((start - next) - step) < 0.0001)
    }

    @Test("한 달이 넘치면 펼친 기둥이 가장자리 안쪽에 머물도록 줄을 민다")
    func longMonthKeepsFocusInside() {
        var shift: CGFloat?
        for position in stride(from: 0, through: 29, by: 1) {
            let layout = RecordPillarLayout(entryCount: 30, position: CGFloat(position), width: Self.width, previousShift: shift)
            shift = layout.shift
            #expect(layout.focusX >= 150 / 2 + 24 - 0.0001 || abs(layout.centers[29] - layout.widths[29] / 2 - 20) < 0.0001)
            #expect(layout.focusX <= Self.width - 150 / 2 - 24 + 0.0001 || abs(layout.centers[0] + layout.widths[0] / 2 - (Self.width - 20)) < 0.0001)
        }
    }

    @Test("끝을 넘겨 끌면 기둥 폭은 그대로 두고 줄 전체가 밀린다")
    func overscrollMovesWholeRow() {
        let atEnd = RecordPillarLayout(entryCount: 13, position: 12, width: Self.width, previousShift: nil)
        let beyond = RecordPillarLayout(entryCount: 13, position: 12.5, width: Self.width, previousShift: nil)

        #expect(atEnd.widths == beyond.widths)
        #expect(beyond.centers[12] < atEnd.centers[12])
    }
}

@MainActor
struct RecordBookTests {
    private static let october = RecordMonth(year: 2026, month: 10)
    private static let now = october.date(day: 13)

    private static var model: RecordBookModel {
        RecordBookModel(
            month: october,
            posts: [1, 5, 13].map { RecordWeekAndYearViewModelTests.post(id: "\($0)", day: $0, month: october) },
            now: now
        )
    }

    @Test("표지와 속표지 다음에 1일부터 오늘까지 하루씩 한 장이다")
    func spreadsFollowDays() {
        let model = Self.model

        #expect(model.maxSpread == 14)
        #expect(model.day(atSpread: 0) == nil)
        #expect(model.day(atSpread: 1) == nil)
        #expect(model.day(atSpread: 2)?.day == 1)
        #expect(model.post(atSpread: 14)?.postId == "13")
        #expect(model.photoCount == 3)
    }

    @Test("묶음 위치 0(가장 최근 날)이 마지막 펼침면이다")
    func positionMapsToSpread() {
        let model = Self.model

        #expect(model.spread(for: 0) == 14)
        #expect(model.spread(for: 12) == 2)
        #expect(model.spread(for: 14) == 0)
    }

    @Test("리본은 이번 달 책에만 오늘 날짜로 단다")
    func ribbonOnlyInThisMonth() {
        #expect(Self.model.hasRibbon)
        #expect(Self.model.todayDay == 13)

        let september = RecordBookModel(month: RecordMonth(year: 2026, month: 9), posts: [], now: Self.now)
        #expect(!september.hasRibbon)
        #expect(september.maxSpread == 31)
    }

    @Test("펼친 책은 정면이라 그린 자리와 누를 자리가 같고, 한 면 책은 위쪽이 멀어지게 조금 눕는다")
    func tiltPerLayout() {
        let spread = RecordBookGeometry(layout: .spread)
        let point = CGPoint(x: 120, y: 80)
        #expect(abs(spread.project(point).x - point.x) < 0.0001)
        #expect(abs(spread.project(point).y - point.y) < 0.0001)

        let single = RecordBookGeometry(layout: .single)
        let top = single.projectedFrame(of: CGRect(x: 100, y: 60, width: 200, height: 1))
        let bottom = single.projectedFrame(of: CGRect(x: 100, y: 250, width: 200, height: 1))
        let anchor = RecordBookGeometry.tiltAnchor
        #expect(top.width < bottom.width)
        #expect(abs(single.project(anchor).x - anchor.x) < 0.0001)
        #expect(abs(single.project(anchor).y - anchor.y) < 0.0001)
    }

    @Test("한 면 책은 덮였을 때 표지가 화면 가운데에 온다")
    func singleCoverIsCentered() {
        let single = RecordBookGeometry(layout: .single)
        let center = single.spineX + single.pageSize.width / 2
        #expect(abs(center - RecordBookGeometry.stageSize.width / 2) < 0.5)
    }

    @Test("사진은 원래 비율 그대로 면 안에 들어간다", arguments: [9.0 / 16.0, 3.0 / 4.0, 1.0, 4.0 / 3.0, 3.0 / 2.0])
    func photoKeepsRatio(ratio: CGFloat) {
        for layout in RecordBookLayout.allCases {
            let geometry = RecordBookGeometry(layout: layout)
            let page = CGRect(origin: CGPoint(x: geometry.spineX, y: geometry.pageTop), size: geometry.pageSize)
            let photo = geometry.photoFrame(ratio: ratio)
            #expect(abs(photo.width / photo.height - ratio) < 0.001)
            #expect(page.contains(photo))
        }
    }

    @Test("한 면 책에서 세로·정사각 사진은 캡션 칸을, 가로 사진은 캡션 줄을 남긴다")
    func singleLeavesCaptionRoom() {
        let single = RecordBookGeometry(layout: .single)
        let pageMaxX = single.spineX + single.pageSize.width
        let pageMaxY = single.pageTop + single.pageSize.height

        let tall = single.photoFrame(ratio: 3.0 / 4.0)
        #expect(pageMaxX - tall.maxX >= RecordBookMetrics.singleSideColumn)
        #expect(tall.height == single.pageSize.height - RecordBookMetrics.singleInset * 2)

        let wide = single.photoFrame(ratio: 4.0 / 3.0)
        #expect(pageMaxY - wide.maxY >= RecordBookMetrics.singleCaptionHeight)
    }

    @Test("펼친 책 화살표는 책 양 끝 바깥, 화면 안에 놓인다")
    func spreadArrowsSitOutsideBook() {
        let spread = RecordBookGeometry(layout: .spread)
        let half = RecordBookMetrics.minimumTouch / 2
        let leading = spread.arrowCenterX(leading: true)
        let trailing = spread.arrowCenterX(leading: false)

        #expect(leading - half >= 0)
        #expect(trailing + half <= RecordBookGeometry.stageSize.width)
        #expect(leading < spread.spineX - spread.coverSize.width)
        #expect(trailing > spread.spineX + spread.coverSize.width)
    }

    @Test("리본 꼬리표의 누를 자리는 44pt 이상이고 무대 안에 있다")
    func ribbonTouchIsLargeEnough() {
        for layout in RecordBookLayout.allCases {
            let frame = RecordBookGeometry(layout: layout).ribbonTouchFrame
            #expect(frame.width >= 44)
            #expect(frame.height >= 44)
            #expect(frame.maxX <= RecordBookGeometry.stageSize.width)
        }
    }
}

struct RecordPhotoColorExtractorTests {
    @Test("단색 사진은 그 색을 대표색으로 돌려준다")
    func solidImageKeepsItsColor() throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 20))
        let image = renderer.image { context in
            UIColor(red: 0.2, green: 0.4, blue: 0.8, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 20))
        }
        let cgImage = try #require(image.cgImage)
        let color = try #require(RecordPhotoColorExtractor.color(from: cgImage))

        // 채도를 조금 올리므로 파란 계열이 유지되는지만 본다.
        #expect(color.blue > color.green)
        #expect(color.green > color.red)
        #expect(abs(color.blue - 0.8) < 0.05)
    }
}
