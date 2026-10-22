import XCTest

final class HomeScreenUITests: XCTestCase {
    @MainActor
    func testMockFeedbackFlowOnApprovedPhoto() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview"]
        app.launch()

        let feedbackButton = app.buttons["이 사진, AI에게 피드백 받아보기"]
        XCTAssertTrue(feedbackButton.waitForExistence(timeout: 5))
        feedbackButton.tap()
        XCTAssertTrue(app.staticTexts["AI 피드백을 시작할까요?"].waitForExistence(timeout: 3))
        captureScreenshot(app, named: "Home feedback sheet")

        let sheetTitle = app.staticTexts["AI 피드백을 시작할까요?"]
        let dragStart = sheetTitle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0))
            .withOffset(CGVector(dx: 0, dy: -30))
        dragStart.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.98)))
        XCTAssertTrue(feedbackButton.wait(for: \.isHittable, toEqual: true, timeout: 3))
        feedbackButton.tap()
        app.buttons["분석 시작하기"].tap()

        let completedButton = app.buttons["AI 피드백이 완료되었어요"]
        XCTAssertTrue(completedButton.waitForExistence(timeout: 8))
        completedButton.tap()
        let expanded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "펼쳐짐"), object: completedButton)
        XCTAssertEqual(XCTWaiter.wait(for: [expanded], timeout: 3), .completed)
        XCTAssertTrue(app.staticTexts["건물의 크기와 높이 차이가 잘 비교되는 구도예요"].exists)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).press(
            forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)),
            withVelocity: .slow,
            thenHoldForDuration: 0.2
        )
        captureScreenshot(app, named: "Home feedback expanded")
        app.buttons["작가님의 해석이 돋보이는 작품이에요"].tap()
        let interpretationTip = app.staticTexts["구도 점수와 별개로, 주제 해석이 특별한 사진이라는 의미예요."]
        XCTAssertTrue(interpretationTip.waitForExistence(timeout: 3))
        captureScreenshot(app, named: "Home feedback interpretation tip")
        app.staticTexts["좋았던 점"].tap()
        XCTAssertTrue(interpretationTip.waitForNonExistence(timeout: 3))
        completedButton.tap()
        XCTAssertFalse(app.staticTexts["건물의 크기와 높이 차이가 잘 비교되는 구도예요"].exists)
    }

    @MainActor
    func testHomeSectionsAndGuestPhotoEntry() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview"]
        app.launch()

        XCTAssertTrue(app.buttons["home.notifications"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["홈"].value as? String, "선택됨")
        XCTAssertTrue(app.staticTexts["이번 주 기록"].exists)
        XCTAssertTrue(app.staticTexts["5/7"].exists)

        app.swipeUp()
        let photo = app.buttons["랭킹 사진 1"].firstMatch
        for _ in 0..<4 where !photo.isHittable { app.swipeUp() }
        XCTAssertTrue(photo.isHittable)
        captureScreenshot(app, named: "Home rankings")
        photo.tap()
        XCTAssertTrue(app.staticTexts["상세를 보려면 로그인이 필요해요"].waitForExistence(timeout: 3))
    }

    @MainActor
    func testHomeScrollPositionSurvivesTabRoundTrip() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview"]
        app.launch()

        let photo = app.buttons["랭킹 사진 1"].firstMatch
        XCTAssertTrue(app.buttons["home.notifications"].waitForExistence(timeout: 5))
        for _ in 0..<6 where !photo.isHittable { app.swipeUp() }
        XCTAssertTrue(photo.isHittable)
        let originalY = photo.frame.minY

        app.buttons["설정"].tap()
        XCTAssertTrue(app.staticTexts["앱 설정"].waitForExistence(timeout: 5))
        app.buttons["홈"].tap()
        XCTAssertTrue(photo.wait(for: \.isHittable, toEqual: true, timeout: 5))
        XCTAssertEqual(photo.frame.minY, originalY, accuracy: 8)

        // 홈을 다시 누르는 명시적인 맨 위 이동은 유지한다.
        app.buttons["홈"].tap()
        XCTAssertTrue(app.staticTexts["하늘"].wait(for: \.isHittable, toEqual: true, timeout: 5))
    }

    @MainActor
    func testIssueOpensWebScreenAndBackReturnsHome() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview"]
        app.launch()

        XCTAssertTrue(app.buttons["home.notifications"].waitForExistence(timeout: 5))
        let issue = app.buttons["함께 만들어주신 찰캌"]
        for _ in 0..<6 where !issue.isHittable { app.swipeUp() }
        XCTAssertTrue(issue.isHittable)
        captureScreenshot(app, named: "Issue cards")
        issue.tap()
        XCTAssertTrue(app.buttons["issue.back"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["issue.title"].exists)
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 5))
        app.buttons["issue.back"].tap()
        XCTAssertTrue(issue.waitForExistence(timeout: 3))

        app.buttons["홈"].tap()
        XCTAssertTrue(app.staticTexts["하늘"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["하늘"].isHittable)
    }

    @MainActor
    func testEmptyAndFailedBannersHideIssueSection() {
        let app = XCUIApplication()
        for state in ["empty", "error"] {
            app.launchArguments = ["-test-home-preview", "-test-home-banner-state", state]
            app.launch()
            XCTAssertTrue(app.buttons["home.notifications"].waitForExistence(timeout: 5))
            for _ in 0..<6 { app.swipeUp() }
            XCTAssertFalse(app.staticTexts["ISSUE"].exists)
            XCTAssertFalse(app.buttons["함께 만들어주신 찰캌"].exists)
            app.terminate()
        }
    }

    @MainActor
    func testIssuePaginationLoadsSecondPageAndStopsAtLastPage() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview", "-test-home-banner-state", "pagination"]
        app.launch()
        XCTAssertTrue(app.buttons["home.notifications"].waitForExistence(timeout: 5))

        let first = app.buttons["이슈 1"]
        for _ in 0..<8 where !first.isHittable { app.swipeUp() }
        XCTAssertTrue(first.isHittable)
        XCTAssertFalse(app.buttons["이슈 11"].exists)

        let nextPage = app.buttons["이슈 11"]
        for _ in 0..<20 where !nextPage.isHittable { app.swipeUp() }
        XCTAssertTrue(nextPage.isHittable)

        let last = app.buttons["이슈 20"]
        for _ in 0..<20 where !last.isHittable { app.swipeUp() }
        XCTAssertTrue(last.isHittable)
        for _ in 0..<3 { app.swipeUp() }
        XCTAssertFalse(app.buttons["이슈 21"].exists)
        XCTAssertFalse(app.otherElements["home.issue.loadingMore"].exists)
        XCTAssertFalse(app.buttons["home.issue.retry"].exists)
        captureScreenshot(app, named: "Issue last page")
    }

    @MainActor
    func testFlatBackButtonRespondsAcrossTouchTarget() {
        let app = XCUIApplication()
        app.launchArguments = ["-test-home-preview", "-use-flat-ios-design"]
        app.launch()

        let issue = app.buttons["함께 만들어주신 찰캌"]
        for _ in 0..<6 where !issue.isHittable { app.swipeUp() }
        XCTAssertTrue(issue.isHittable)
        issue.tap()

        let backButton = app.buttons["issue.back"]
        XCTAssertTrue(backButton.waitForExistence(timeout: 5))
        backButton.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.5)).tap()
        XCTAssertTrue(issue.waitForExistence(timeout: 3))
    }

    @MainActor
    func testHeroStatesAndResubmitEntry() {
        let app = XCUIApplication()
        let scenarios = ["rejected-open", "rejected-closed", "empty", "pending", "approved", "processing", "loading", "error", "no-open-topic"]
        for scenario in scenarios {
            app.launchArguments = ["-test-home-preview", "-test-home-hero-state", scenario, "-test-photo-upload-entry"]
            app.launch()
            XCTAssertTrue(app.staticTexts["하늘"].waitForExistence(timeout: 5))
            if scenario == "rejected-open" {
                XCTAssertTrue(app.buttons["사진 다시 제출하기"].exists)
            } else if scenario == "rejected-closed" {
                XCTAssertFalse(app.buttons["사진 다시 제출하기"].exists)
                XCTAssertTrue(app.staticTexts["사진이 반려되었어요\n이 주제의 참여 기간이 종료되었어요"].exists)
            }
            captureScreenshot(app, named: "hero-\(scenario)")
            if scenario == "rejected-open" || scenario == "empty" {
                let label = scenario == "rejected-open" ? "사진 다시 제출하기" : "하늘 사진을 공유하지 않았어요"
                app.buttons[label].tap()
                XCTAssertTrue(app.buttons["photoUpload.close"].waitForExistence(timeout: 5))
            }
            app.terminate()
        }
    }

    @MainActor
    private func captureScreenshot(_ app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
