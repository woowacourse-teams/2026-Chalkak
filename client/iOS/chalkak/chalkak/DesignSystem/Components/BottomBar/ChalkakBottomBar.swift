import SwiftUI

enum ChalkakBottomBarItem: String, CaseIterable, Identifiable {
    case today
    case display
    case record
    case settings

    var id: Self { self }

    var label: String {
        switch self {
        case .today:
            "오늘"
        case .display:
            "전시"
        case .record:
            "기록"
        case .settings:
            "설정"
        }
    }

    var iconName: String {
        switch self {
        case .today:
            "ic_bottom_today"
        case .display:
            "ic_bottom_display"
        case .record:
            "ic_bottom_record"
        case .settings:
            "ic_bottom_setting"
        }
    }
}

struct ChalkakBottomBar: View {
    @Environment(\.chalkakTheme) private var theme
    let selectedItem: ChalkakBottomBarItem
    let onSelect: (ChalkakBottomBarItem) -> Void
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            itemButton(.today)
            itemButton(.display)
            addButton
            itemButton(.record)
            itemButton(.settings)
        }
        .padding(.top, Metrics.topPadding)
        .padding(.bottom, Metrics.bottomPadding)
        .background(theme.colors.surfaceElevated)
    }

    private func itemButton(_ item: ChalkakBottomBarItem) -> some View {
        let isSelected = item == selectedItem
        let color = isSelected ? theme.colors.actionPrimary : theme.colors.bottomBar

        return Button {
            onSelect(item)
        } label: {
            VStack(spacing: Metrics.itemSpacing) {
                Image(item.iconName)
                    .renderingMode(.template)
                    .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                    .accessibilityHidden(true)

                Text(item.label)
                    .font(theme.typography.footnote)
                    .fontWeight(isSelected ? .bold : .regular)
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, minHeight: Metrics.minimumTouchSize)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.label)
        .accessibilityValue(isSelected ? "선택됨" : "")
    }

    private var addButton: some View {
        Button(action: onAdd) {
            Image("ic_bottom_write")
                .renderingMode(.original)
                .frame(width: Metrics.addButtonSize, height: Metrics.addButtonSize)
                .frame(maxWidth: .infinity, minHeight: Metrics.minimumTouchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("추가")
    }
}

private enum Metrics {
    static let topPadding: CGFloat = 15
    static let bottomPadding: CGFloat = 12
    static let itemSpacing: CGFloat = 7
    static let iconSize: CGFloat = 23
    static let addButtonSize: CGFloat = 40
    static let minimumTouchSize: CGFloat = 48
}

#Preview("Bottom Bar") {
    @Previewable @State var selection = ChalkakBottomBarItem.today

    ChalkakBottomBar(
        selectedItem: selection,
        onSelect: { selection = $0 },
        onAdd: {}
    )
}
