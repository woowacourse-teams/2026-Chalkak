import SwiftUI
import Testing
import UIKit
@testable import chalkak

struct BottomBarIconTests {
    @Test("모든 탭은 선택 여부에 맞는 에셋을 사용한다")
    func selectsMatchingAsset() {
        let items: [(ChalkakBottomBarItem, String)] = [
            (.home, "ic_bottom_home"),
            (.display, "ic_bottom_display"),
            (.record, "ic_bottom_record"),
            (.filter, "ic_bottom_setting")
        ]
        for (item, name) in items {
            #expect(item.icon(isSelected: false) == Image(name))
            #expect(item.icon(isSelected: true) == Image("\(name)_selected"))
            #expect(item.icon(isSelected: false) != item.icon(isSelected: true))
            #expect(UIImage(named: name) != nil)
            #expect(UIImage(named: "\(name)_selected") != nil)
        }
    }

    @Test("하단 탭 마지막 칸은 설정 아이콘을 쓰는 필터다")
    func lastTabIsFilterWithSettingIcon() throws {
        // Given 하단 탭 항목들
        // When  항목 순서와 마지막 칸의 이름·아이콘을 본다
        // Then  순서가 홈, 전시, 기록, 필터이고 필터의 이름은 "필터", 아이콘은 ic_bottom_setting(선택 시 ic_bottom_setting_selected)
        let items = ChalkakBottomBarItem.allCases

        #expect(items.map(\.rawValue) == ["home", "display", "record", "filter"])
        #expect(items.map(\.label) == ["홈", "전시", "기록", "필터"])
        let filter = try #require(items.last)
        #expect(filter.icon(isSelected: false) == Image("ic_bottom_setting"))
        #expect(filter.icon(isSelected: true) == Image("ic_bottom_setting_selected"))
    }
}
