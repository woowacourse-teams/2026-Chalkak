import SwiftUI

struct ChalkakSortSelector<Option: Hashable>: View {
    @Environment(\.chalkakTheme) private var theme
    let options: [Option]
    let selectedOption: Option
    let label: (Option) -> String
    let onSelect: (Option) -> Void

    var body: some View {
        HStack(spacing: theme.spacing.lg) {
            ForEach(options, id: \.self) { option in
                sortButton(for: option)
            }
        }
    }

    private func sortButton(for option: Option) -> some View {
        let isSelected = option == selectedOption

        return Button {
            onSelect(option)
        } label: {
            Text(label(option))
                .font(theme.typography.subheadline)
                .foregroundStyle(
                    isSelected ? theme.colors.textPrimary : theme.colors.textInactive
                )
                .padding(.bottom, theme.spacing.xs)
                .overlay(alignment: .bottom) {
                    if isSelected {
                        Rectangle()
                            .fill(theme.colors.textPrimary)
                            .frame(height: Metrics.indicatorHeight)
                    }
                }
                .frame(minHeight: Metrics.minimumTouchHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(isSelected ? "선택됨" : "")
    }
}

private enum Metrics {
    static let indicatorHeight: CGFloat = 1
    static let minimumTouchHeight: CGFloat = 44
}

private enum PreviewSort: String, CaseIterable {
    case latest = "최신순"
    case popular = "인기순"
    case random = "랜덤순"
}

#Preview("Sort Selector") {
    @Previewable @State var selection = PreviewSort.latest

    ChalkakSortSelector(
        options: PreviewSort.allCases,
        selectedOption: selection,
        label: \.rawValue,
        onSelect: { selection = $0 }
    )
    .padding()
}
