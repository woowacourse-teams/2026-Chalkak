//
//  chalkakUITests.swift
//  chalkakUITests
//
//  Created by 정찬 on 8/9/26.
//

import XCTest

final class chalkakUITests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testBottomBarScrubSelectsReleasePositionAndCanSkipTabs() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let today = app.buttons["오늘"]
        let display = app.buttons["전시"]
        let settings = app.buttons["설정"]
        XCTAssertTrue(today.waitForExistence(timeout: 5))

        func drag(from startButton: XCUIElement, to endButton: XCUIElement) {
            startButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                .press(forDuration: 0.05, thenDragTo: endButton.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
                ))
        }

        drag(from: today, to: display)
        XCTAssertEqual(display.value as? String, "선택됨")
        drag(from: display, to: settings)
        XCTAssertEqual(settings.value as? String, "선택됨")
        XCTAssertFalse(app.staticTexts["전시하기"].exists)
        drag(from: settings, to: today)
        XCTAssertEqual(today.value as? String, "선택됨")

        display.tap()
        XCTAssertEqual(display.value as? String, "선택됨")
        today.tap()
        XCTAssertEqual(today.value as? String, "선택됨")
    }

    @MainActor
    func testBottomBarScrubCanStartOnUnselectedTab() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()
        XCTAssertTrue(app.buttons["오늘"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["오늘"].value as? String, "선택됨")

        app.buttons["설정"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.4, thenDragTo: app.buttons["전시"].coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertEqual(app.buttons["전시"].value as? String, "선택됨")
        XCTAssertFalse(app.staticTexts["전시하기"].exists)
    }

    @MainActor
    func testBottomBarScrubWithinSameTabKeepsSelectionAndAddOpensOnRelease() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let today = app.buttons["오늘"]
        XCTAssertTrue(today.waitForExistence(timeout: 5))
        let start = today.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 14, dy: 0)))
        XCTAssertEqual(today.value as? String, "선택됨")
        start.press(forDuration: 0.05, thenDragTo: app.buttons["추가"].coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
        ))
        XCTAssertTrue(app.staticTexts["전시하기"].waitForExistence(timeout: 5))
        app.buttons["뒤로 가기"].tap()
        XCTAssertEqual(today.value as? String, "선택됨")
        app.buttons["추가"].tap()
        XCTAssertTrue(app.staticTexts["전시하기"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testCompactBottomBarScrubSelectsReleasePositionAndExpands() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let today = app.buttons["오늘"]
        let settings = app.buttons["설정"]
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        XCTAssertTrue(app.staticTexts["앱 설정"].waitForExistence(timeout: 5))
        let expandedWidth = today.frame.width
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(today.frame.width, expandedWidth)

        settings.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: today.coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertEqual(today.value as? String, "선택됨")
        XCTAssertEqual(today.frame.width, expandedWidth, accuracy: 1)

        settings.tap()
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(today.frame.width, expandedWidth)
        settings.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: app.buttons["추가"].coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertTrue(app.staticTexts["전시하기"].waitForExistence(timeout: 5))
        app.buttons["뒤로 가기"].tap()
        XCTAssertEqual(settings.value as? String, "선택됨")
        XCTAssertEqual(today.frame.width, expandedWidth, accuracy: 1)
    }

    @MainActor
    func testExample() throws {
        // UI tests must launch the application that they test.
        let app = XCUIApplication()
        app.launch()

        // Use XCTAssert and related functions to verify your tests produce the correct results.
        // XCUIAutomation Documentation
        // https://developer.apple.com/documentation/xcuiautomation
    }

    @MainActor
    func testTermsViewButtonsOpenTheirLegalDocumentSheets() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-show-onboarding"]
        app.launch()

        let viewButtons = app.buttons.matching(
            NSPredicate(format: "label == %@", "보기")
        )
        XCTAssertEqual(viewButtons.count, 2)

        viewButtons.element(boundBy: 0).tap()
        let termsSheet = app.descendants(matching: .any)[
            "legalDocumentSheet.termsOfService"
        ]
        XCTAssertTrue(termsSheet.waitForExistence(timeout: 3))

        app.buttons["닫기"].tap()
        XCTAssertTrue(termsSheet.waitForNonExistence(timeout: 3))

        viewButtons.element(boundBy: 1).tap()
        XCTAssertTrue(
            app.descendants(matching: .any)["legalDocumentSheet.privacyPolicy"]
                .waitForExistence(timeout: 3)
        )
    }

    @MainActor
    func testSettingsUploadButtonShowsLoginRequiredMessageForGuest() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "-stonefive.chalkak.guest-access", "NO",
            "-notificationSetup.hasCompleted", "YES"
        ]
        app.launch()

        let guestButton = app.buttons["로그인 없이 사진 둘러보기"]
        XCTAssertTrue(guestButton.waitForExistence(timeout: 5))
        guestButton.tap()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()

        let uploadButton = app.buttons["추가"]
        XCTAssertTrue(uploadButton.waitForExistence(timeout: 5))
        uploadButton.tap()

        XCTAssertTrue(
            app.staticTexts["게시물을 추가하려면 로그인이 필요해요"]
                .waitForExistence(timeout: 3)
        )
    }

    @MainActor
    func testGuestSettingsSeparatesFeedbackAndAccountSections() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "-stonefive.chalkak.guest-access", "NO",
            "-notificationSetup.hasCompleted", "YES"
        ]
        app.launch()

        let guestButton = app.buttons["로그인 없이 사진 둘러보기"]
        if guestButton.waitForExistence(timeout: 2) {
            guestButton.tap()
        }
        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()

        if app.buttons["로그아웃"].exists {
            app.buttons["로그아웃"].tap()
            app.buttons["confirmDialog.confirm"].tap()
            XCTAssertTrue(guestButton.waitForExistence(timeout: 5))
            guestButton.tap()
            settingsButton.tap()
        }

        XCTAssertTrue(app.staticTexts["정보 및 약관"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["피드백"].exists)
        XCTAssertTrue(app.staticTexts["계정"].exists)
        XCTAssertFalse(app.staticTexts["앱 설정"].exists)
        XCTAssertTrue(app.buttons["로그인"].exists)
        XCTAssertLessThan(app.staticTexts["계정"].frame.minY, app.staticTexts["피드백"].frame.minY)
        XCTAssertLessThan(
            app.staticTexts["피드백"].frame.minY,
            app.staticTexts["정보 및 약관"].frame.minY
        )

        app.buttons["피드백 보내기"].tap()
        XCTAssertTrue(
            app.staticTexts["피드백을 보내려면 로그인이 필요해요"]
                .waitForExistence(timeout: 3)
        )
    }

    @MainActor
    func testSettingsUploadButtonOpensPhotoUploadAndReturnsToSettingsForAuthenticatedUser() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()

        let uploadButton = app.buttons["추가"]
        XCTAssertTrue(uploadButton.waitForExistence(timeout: 5))
        uploadButton.tap()

        XCTAssertTrue(app.staticTexts["전시하기"].waitForExistence(timeout: 5))
        app.buttons["뒤로 가기"].tap()

        XCTAssertTrue(app.staticTexts["앱 설정"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["설정"].value as? String, "선택됨")
    }

    @MainActor
    func testSettingsNotificationFullScreenClosesAndReturnsToSettings() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()

        let notificationButton = app.buttons["알림 설정"]
        XCTAssertTrue(notificationButton.waitForExistence(timeout: 5))
        notificationButton.tap()

        let title = app.staticTexts["notificationSetup.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        let closeButton = app.buttons["notificationSetup.close"]
        XCTAssertTrue(closeButton.exists)
        XCTAssertFalse(app.buttons["뒤로 가기"].exists)
        closeButton.tap()

        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(notificationButton.isHittable)
        XCTAssertEqual(settingsButton.value as? String, "선택됨")

        notificationButton.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        title.swipeDown()
        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(notificationButton.isHittable)
        XCTAssertEqual(settingsButton.value as? String, "선택됨")

        notificationButton.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        app.buttons["notificationTime.custom"].tap()
        let pickerDragHandle = app.otherElements["notificationTimePicker.dragHandle"]
        XCTAssertTrue(pickerDragHandle.waitForExistence(timeout: 3))
        pickerDragHandle.swipeDown()
        XCTAssertTrue(pickerDragHandle.waitForNonExistence(timeout: 3))
        XCTAssertTrue(title.exists)

        app.buttons["notificationSetup.skip"].tap()
        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(notificationButton.isHittable)
        XCTAssertEqual(settingsButton.value as? String, "선택됨")
    }

    @MainActor
    func testFeedbackFullScreenClosesWithButtonAndDownwardSwipe() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-feedback-entry", "-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()
        let feedbackButton = app.buttons["피드백 보내기"]
        XCTAssertTrue(feedbackButton.waitForExistence(timeout: 5))
        feedbackButton.tap()

        let title = app.staticTexts["찰캌을 사용하며\n느낀 점을 알려주세요."]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["뒤로 가기"].exists)
        let closeButton = app.buttons["feedback.close"]
        XCTAssertTrue(closeButton.isHittable)
        XCTAssertGreaterThan(closeButton.frame.midX, app.frame.midX)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Feedback full screen"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        closeButton.tap()
        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(feedbackButton.isHittable)

        feedbackButton.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: title.coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
        ).withOffset(CGVector(dx: 0, dy: 300)))
        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(feedbackButton.isHittable)
        XCTAssertEqual(settingsButton.value as? String, "선택됨")
    }

    @MainActor
    func testFeedbackInputDragPreservesDraft() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-feedback-entry", "-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()
        let feedbackButton = app.buttons["피드백 보내기"]
        XCTAssertTrue(feedbackButton.waitForExistence(timeout: 5))
        feedbackButton.tap()

        let title = app.staticTexts["찰캌을 사용하며\n느낀 점을 알려주세요."]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        let input = app.textFields["피드백 내용"]
        XCTAssertTrue(input.waitForExistence(timeout: 3))
        input.tap()
        input.typeText("Draft to preserve")
        let dragStart = input.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        dragStart.press(forDuration: 0.05, thenDragTo: dragStart.withOffset(CGVector(dx: 0, dy: 160)))

        XCTAssertTrue(title.exists)
        XCTAssertTrue((input.value as? String)?.contains("Draft to preserve") == true)

        let headerDragStart = title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        headerDragStart.press(forDuration: 0.05, thenDragTo: headerDragStart.withOffset(CGVector(dx: 0, dy: 300)))
        XCTAssertTrue(title.waitForNonExistence(timeout: 3))
        XCTAssertTrue(feedbackButton.isHittable)
    }

    @MainActor
    func testNotificationSetupSkipContinuesToHome() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-show-notification-setup"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["notificationSetup.title"]
                .waitForExistence(timeout: 5)
        )

        app.buttons["notificationTime.custom"].tap()
        let pickerDragHandle = app.otherElements["notificationTimePicker.dragHandle"]
        XCTAssertTrue(pickerDragHandle.waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["이 시간 선택하기"].exists)
        pickerDragHandle.swipeDown()
        XCTAssertTrue(pickerDragHandle.waitForNonExistence(timeout: 3))

        app.buttons["notificationTime.custom"].tap()
        app.buttons["이 시간 선택하기"].tap()
        XCTAssertTrue(
            app.buttons["notificationTime.custom"].label.contains("직접 설정 ·")
        )

        app.buttons["notificationSetup.skip"].tap()

        XCTAssertTrue(app.buttons["오늘"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["notificationSetup.title"].exists)
    }

    @MainActor
    func testDailyReminderTapOnHomeReloadsHome() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-daily-reminder-tap"]
        app.launch()

        let emptyHome = app.descendants(matching: .any)["home-empty"]
        XCTAssertTrue(emptyHome.waitForExistence(timeout: 5))

        // 앱이 실행 1초 뒤 알림 탭 이벤트를 흉내 낸다.
        Thread.sleep(forTimeInterval: 2)

        XCTAssertTrue(emptyHome.waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["home-loading"].exists)
    }

    @MainActor
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
