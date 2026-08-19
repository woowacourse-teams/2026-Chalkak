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

    var systemImage: String {
        switch self {
        case .today:
            "photo.on.rectangle.angled"
        case .display:
            "rectangle.stack"
        case .record:
            "clock.arrow.circlepath"
        case .settings:
            "gearshape"
        }
    }
}

struct ChalkakBottomBar: View {
    @Environment(\.chalkakColors) private var colors

    let selectedItem: ChalkakBottomBarItem
    let onSelect: (ChalkakBottomBarItem) -> Void
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: ChalkakSpacing.none) {
            itemButton(.today)
            itemButton(.display)
            addButton
            itemButton(.record)
            itemButton(.settings)
        }
        .padding(.top, Metrics.topPadding)
        .padding(.bottom, Metrics.bottomPadding)
        .background(colors.surfaceElevated)
    }

    private func itemButton(_ item: ChalkakBottomBarItem) -> some View {
        let isSelected = item == selectedItem
        let color = isSelected ? colors.actionPrimary : colors.bottomBar

        return Button {
            onSelect(item)
        } label: {
            VStack(spacing: Metrics.itemSpacing) {
                Image(systemName: item.systemImage)
                    .font(.system(size: Metrics.iconSize, weight: .regular))
                    .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                    .accessibilityHidden(true)

                Text(item.label)
                    .font(ChalkakTypography.footnote)
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
            Image(systemName: "plus")
                .font(.system(size: Metrics.addIconSize, weight: .medium))
                .foregroundStyle(colors.onActionPrimary)
                .frame(width: Metrics.addButtonSize, height: Metrics.addButtonSize)
                .background(colors.actionPrimary, in: Circle())
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
    static let addIconSize: CGFloat = 18
    static let addButtonSize: CGFloat = 44
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
