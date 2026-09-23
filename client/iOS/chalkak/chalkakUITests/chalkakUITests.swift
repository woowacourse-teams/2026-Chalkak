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
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
