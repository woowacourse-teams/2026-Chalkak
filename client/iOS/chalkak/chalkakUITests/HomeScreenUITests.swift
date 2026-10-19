import XCTest

final class HomeScreenUITests: XCTestCase {
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
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 5))
        app.buttons["issue.back"].tap()
        XCTAssertTrue(issue.waitForExistence(timeout: 3))

        app.buttons["홈"].tap()
        XCTAssertTrue(app.staticTexts["하늘"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["하늘"].isHittable)
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
