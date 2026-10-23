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
            (.settings, "ic_bottom_setting")
        ]
        for (item, name) in items {
            #expect(item.icon(isSelected: false) == Image(name))
            #expect(item.icon(isSelected: true) == Image("\(name)_selected"))
            #expect(item.icon(isSelected: false) != item.icon(isSelected: true))
            #expect(UIImage(named: name) != nil)
            #expect(UIImage(named: "\(name)_selected") != nil)
        }
    }
}
