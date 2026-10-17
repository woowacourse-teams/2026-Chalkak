import SwiftUI
import Testing
import UIKit
@testable import chalkak

struct BottomBarIconTests {
    @Test("홈은 커스텀 에셋 대신 SF Symbols의 house를 사용한다")
    func homeUsesSystemHouse() {
        #expect(ChalkakBottomBarItem.home.icon == Image(systemName: "house"))
        #expect(UIImage(systemName: "house") != nil)
        #expect(ChalkakBottomBarItem.home.label == "홈")
    }

    @Test("다른 탭의 기존 아이콘은 유지한다")
    func preservesOtherTabIcons() {
        #expect(ChalkakBottomBarItem.display.icon == Image("ic_bottom_display"))
        #expect(ChalkakBottomBarItem.record.icon == Image("ic_bottom_record"))
        #expect(ChalkakBottomBarItem.settings.icon == Image("ic_bottom_setting"))
    }
}
