import SwiftUI
import UIKit

/// 휠 한 열의 내용. `titles[selectedIndex]`가 가운데 선택 띠에 놓인다.
struct ChalkakWheelColumn {
    let accessibilityLabel: String
    let titles: [String]
    let selectedIndex: Int
    let onSelect: (Int) -> Void
}

/// 위아래로 굴려 값을 고르는 시스템 휠. 여러 열을 한 `UIPickerView`에 넣어
/// 시스템 날짜 휠처럼 선택 띠가 열을 가로질러 하나로 이어진다.
/// Android `ChalkakWheelPicker`와 역할을 맞춘다.
///
/// - 고를 수 없는 값은 `titles`에 넣지 않는다.
/// - 폭은 호출부가 정한다. 높이는 시스템 휠 고유 높이(216pt)를 쓴다.
/// - VoiceOver는 열마다 `accessibilityLabel`과 현재 값을 읽고, 위아래 스와이프로 값을 바꾼다(시스템 휠 기본 동작).
///
/// 예: `ChalkakWheelPicker(columns: [ChalkakWheelColumn(accessibilityLabel: "월", titles: ["9월", "10월"], selectedIndex: 1, onSelect: { _ in })])`
struct ChalkakWheelPicker: UIViewRepresentable {
    let columns: [ChalkakWheelColumn]

    func makeCoordinator() -> Coordinator {
        Coordinator(columns: columns)
    }

    func makeUIView(context: Context) -> UIPickerView {
        let picker = UIPickerView()
        picker.dataSource = context.coordinator
        picker.delegate = context.coordinator
        picker.setContentHuggingPriority(.defaultLow, for: .horizontal)
        picker.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        selectRows(in: picker, animated: false)
        return picker
    }

    func updateUIView(_ picker: UIPickerView, context: Context) {
        let previousTitles = context.coordinator.columns.map(\.titles)
        context.coordinator.columns = columns
        for (component, column) in columns.enumerated()
        where previousTitles.indices.contains(component) == false || previousTitles[component] != column.titles {
            picker.reloadComponent(component)
        }
        selectRows(in: picker, animated: false)
    }

    // 밖에서 정한 선택값(예: 월을 바꿔 일이 범위 끝으로 당겨짐)으로 휠을 맞춘다.
    // 시스템 날짜 휠처럼 굴러가지 않고 제자리에서 바뀐다.
    private func selectRows(in picker: UIPickerView, animated: Bool) {
        for (component, column) in columns.enumerated()
        where column.titles.indices.contains(column.selectedIndex)
            && picker.selectedRow(inComponent: component) != column.selectedIndex {
            picker.selectRow(column.selectedIndex, inComponent: component, animated: animated)
        }
    }

    final class Coordinator: NSObject, UIPickerViewDataSource, UIPickerViewDelegate, UIPickerViewAccessibilityDelegate {
        var columns: [ChalkakWheelColumn]

        init(columns: [ChalkakWheelColumn]) {
            self.columns = columns
        }

        func numberOfComponents(in pickerView: UIPickerView) -> Int {
            columns.count
        }

        func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
            columns[component].titles.count
        }

        func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
            columns[component].titles[row]
        }

        func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
            guard row != columns[component].selectedIndex else { return }
            columns[component].onSelect(row)
        }

        func pickerView(_ pickerView: UIPickerView, accessibilityLabelForComponent component: Int) -> String? {
            columns[component].accessibilityLabel
        }
    }
}

#Preview("Wheel Picker", traits: .sizeThatFitsLayout) {
    @Previewable @State var monthIndex = 1
    ChalkakWheelPicker(columns: [
        ChalkakWheelColumn(accessibilityLabel: "연도", titles: ["2026년"], selectedIndex: 0, onSelect: { _ in }),
        ChalkakWheelColumn(
            accessibilityLabel: "월",
            titles: ["9월", "10월"],
            selectedIndex: monthIndex,
            onSelect: { monthIndex = $0 }
        ),
    ])
    .chalkakTheme(.light)
}
