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
    func testLaunchPerformance() throws {
        // This measures how long it takes to launch your application.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
