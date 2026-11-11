//
//  chalkakUITests.swift
//  chalkakUITests
//
//  Created by 정찬 on 8/9/26.
//

import XCTest

final class chalkakUITests: XCTestCase {

    @MainActor
    func testPhotoUploadClosesWithoutConfirmationWhenNothingIsSelected() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()
        XCTAssertTrue(app.buttons["추가"].waitForExistence(timeout: 5))
        app.buttons["추가"].tap()

        let close = app.buttons["photoUpload.close"]
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["photoUpload.submit"].isEnabled)
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))
        XCTAssertFalse(app.alerts["작성을 그만둘까요?"].exists)
    }

    @MainActor
    func testPhotoUploadConfirmsDiscardFromCloseAndDownwardSwipe() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry", "-test-photo-upload-selected"]
        app.launch()
        XCTAssertTrue(app.buttons["추가"].waitForExistence(timeout: 5))
        app.buttons["추가"].tap()

        // 사진을 고른 상태에서도 같은 화면에서 제목을 쓴다.
        let close = app.buttons["photoUpload.close"]
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        let caption = app.textFields["작품 제목"]
        XCTAssertTrue(caption.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["photoUpload.submit"].exists)
        caption.tap()
        caption.typeText("작품 제목")

        close.tap()
        let dialogTitle = app.alerts["작성을 그만둘까요?"]
        XCTAssertTrue(dialogTitle.waitForExistence(timeout: 5))
        dialogTitle.buttons["취소"].tap()
        XCTAssertTrue(dialogTitle.waitForNonExistence(timeout: 3))
        XCTAssertTrue((caption.value as? String)?.contains("작품 제목") == true)

        // 제목을 쓰며 올라간 화면을 맨 위로 되돌린다. 아래로 끌어 닫기는 맨 위에서만 동작한다.
        app.swipeDown()
        XCTAssertFalse(dialogTitle.waitForExistence(timeout: 2))

        let imageArea = app.buttons["photoUpload.selectedImage"].exists
            ? app.buttons["photoUpload.selectedImage"]
            : app.images["선택한 사진"]
        XCTAssertTrue(imageArea.waitForExistence(timeout: 5))
        let start = imageArea.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: 300)))
        XCTAssertTrue(dialogTitle.waitForExistence(timeout: 5))
        dialogTitle.buttons["뒤로 가기"].tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))
    }

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

        let today = app.buttons["홈"]
        let display = app.buttons["전시"]
        let filter = app.buttons["필터"]
        XCTAssertTrue(today.waitForExistence(timeout: 5))

        func drag(from startButton: XCUIElement, to endButton: XCUIElement) {
            startButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                .press(forDuration: 0.05, thenDragTo: endButton.coordinate(
                    withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
                ))
        }

        drag(from: today, to: display)
        XCTAssertEqual(display.value as? String, "선택됨")
        drag(from: display, to: filter)
        XCTAssertEqual(filter.value as? String, "선택됨")
        XCTAssertFalse(app.buttons["photoUpload.close"].exists)
        drag(from: filter, to: today)
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
        XCTAssertTrue(app.buttons["홈"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["홈"].value as? String, "선택됨")

        app.buttons["필터"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.4, thenDragTo: app.buttons["전시"].coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertEqual(app.buttons["전시"].value as? String, "선택됨")
        XCTAssertFalse(app.buttons["photoUpload.close"].exists)
    }

    @MainActor
    func testBottomBarScrubWithinSameTabKeepsSelectionAndAddOpensOnRelease() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let today = app.buttons["홈"]
        XCTAssertTrue(today.waitForExistence(timeout: 5))
        let start = today.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 14, dy: 0)))
        XCTAssertEqual(today.value as? String, "선택됨")
        start.press(forDuration: 0.05, thenDragTo: app.buttons["추가"].coordinate(
            withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
        ))
        XCTAssertTrue(app.buttons["photoUpload.close"].waitForExistence(timeout: 5))
        app.buttons["photoUpload.close"].tap()
        XCTAssertEqual(today.value as? String, "선택됨")
        app.buttons["추가"].tap()
        XCTAssertTrue(app.buttons["photoUpload.close"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testCompactBottomBarScrubSelectsReleasePositionAndExpands() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let today = app.buttons["홈"]
        let filter = app.buttons["필터"]
        XCTAssertTrue(filter.waitForExistence(timeout: 5))
        filter.tap()
        XCTAssertTrue(app.staticTexts["필터 1"].waitForExistence(timeout: 5))
        let expandedWidth = today.frame.width
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(today.frame.width, expandedWidth)

        filter.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: today.coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertEqual(today.value as? String, "선택됨")
        XCTAssertEqual(today.frame.width, expandedWidth, accuracy: 1)

        filter.tap()
        app.scrollViews.firstMatch.swipeUp()
        XCTAssertLessThan(today.frame.width, expandedWidth)
        filter.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.05, thenDragTo: app.buttons["추가"].coordinate(
                withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)
            ))
        XCTAssertTrue(app.buttons["photoUpload.close"].waitForExistence(timeout: 5))
        app.buttons["photoUpload.close"].tap()
        XCTAssertEqual(filter.value as? String, "선택됨")
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
    func testFilterUploadButtonShowsLoginRequiredMessageForGuest() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "-stonefive.chalkak.guest-access", "NO"
        ]
        app.launch()

        let guestButton = app.buttons["로그인 없이 사진 둘러보기"]
        XCTAssertTrue(guestButton.waitForExistence(timeout: 5))
        guestButton.tap()

        let filterTab = app.buttons["필터"]
        XCTAssertTrue(filterTab.waitForExistence(timeout: 5))
        filterTab.tap()

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
            "-stonefive.chalkak.guest-access", "NO"
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
            app.alerts.buttons["로그아웃"].tap()
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

        app.buttons["고객센터"].tap()
        XCTAssertTrue(
            app.staticTexts["고객센터를 이용하려면 로그인이 필요해요"]
                .waitForExistence(timeout: 3)
        )
    }

    @MainActor
    func testFilterUploadButtonOpensPhotoUploadAndReturnsToFilterForAuthenticatedUser() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let filterTab = app.buttons["필터"]
        XCTAssertTrue(filterTab.waitForExistence(timeout: 5))
        filterTab.tap()

        let uploadButton = app.buttons["추가"]
        XCTAssertTrue(uploadButton.waitForExistence(timeout: 5))
        uploadButton.tap()

        XCTAssertTrue(app.buttons["photoUpload.close"].waitForExistence(timeout: 5))
        app.buttons["photoUpload.close"].tap()

        XCTAssertTrue(app.staticTexts["필터 1"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["필터"].value as? String, "선택됨")
    }

    @MainActor
    func testNotificationSettingsOpensFromSettingsWithFourTogglesAndReturns() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()
        let settings = app.buttons["설정"]
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let notificationSettings = app.buttons["알림 설정"]
        XCTAssertTrue(notificationSettings.waitForExistence(timeout: 5))
        XCTAssertFalse(app.switches["오늘 주제 알림"].exists)
        notificationSettings.tap()
        let topicToggle = app.switches["오늘 주제 알림"]
        XCTAssertTrue(topicToggle.waitForExistence(timeout: 5))
        XCTAssertTrue(app.switches["게시물 승인·반려 알림"].exists)
        XCTAssertTrue(app.switches["AI 피드백 완료 알림"].exists)
        XCTAssertTrue(app.switches["좋아요 알림"].exists)
        XCTAssertFalse(app.staticTexts["이 계정의 모든 기기에 적용돼요"].exists)
        XCTAssertFalse(app.switches["알림 받기"].exists)
        let permission = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "알림 허용")).firstMatch
        XCTAssertFalse(permission.exists, app.debugDescription)
        app.buttons["뒤로 가기"].tap()
        XCTAssertTrue(notificationSettings.waitForExistence(timeout: 5))
    }

    @MainActor
    func testFeedbackFullScreenClosesWithButtonAndDownwardSwipe() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-feedback-entry", "-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()
        let customerSupportButton = app.buttons["고객센터"]
        XCTAssertTrue(customerSupportButton.waitForExistence(timeout: 5))
        customerSupportButton.tap()
        let feedbackButton = app.buttons["feedbackList.compose"]
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
    }

    @MainActor
    func testFeedbackInputDragPreservesDraft() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-feedback-entry", "-test-photo-upload-entry"]
        app.launch()

        let settingsButton = app.buttons["설정"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: 5))
        settingsButton.tap()
        let customerSupportButton = app.buttons["고객센터"]
        XCTAssertTrue(customerSupportButton.waitForExistence(timeout: 5))
        customerSupportButton.tap()
        let feedbackButton = app.buttons["feedbackList.compose"]
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
    func testHomeNotificationOpensSampleListAndReturnsToHome() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-test-photo-upload-entry"]
        app.launch()

        let notificationButton = app.buttons["home.notifications"]
        XCTAssertTrue(notificationButton.waitForExistence(timeout: 5))
        notificationButton.tap()

        XCTAssertTrue(app.staticTexts["notification.title"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["9월 22일 오늘의 주제를 확인해보세요."].exists)
        XCTAssertTrue(app.staticTexts["9월 21일 오늘의 주제를 확인해보세요."].exists)
        XCTAssertTrue(app.staticTexts["어제 21:10"].exists)
        XCTAssertFalse(app.buttons["홈"].exists)

        app.buttons["notification.back"].tap()
        XCTAssertTrue(notificationButton.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["notification.title"].exists)
    }

    @MainActor
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
