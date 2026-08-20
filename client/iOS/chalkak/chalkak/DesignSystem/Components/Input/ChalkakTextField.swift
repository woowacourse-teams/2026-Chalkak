import SwiftUI

struct ChalkakTextField: View {
    @Environment(\.chalkakTheme) private var theme
    @FocusState private var isFocused: Bool

    @Binding var text: String
    let label: String
    var placeholder = ""
    var isEnabled = true
    var isReadOnly = false
    var lineLimit: ClosedRange<Int> = 1...5
    var maximumCharacterCount: Int?
    var showsCharacterCount = true
    var height: CGFloat?

    var body: some View {
        TextField(
            label,
            text: limitedText,
            prompt: Text(placeholder)
                .foregroundStyle(theme.colors.textInactive),
            axis: lineLimit.upperBound == 1 ? .horizontal : .vertical
        )
        .labelsHidden()
        .lineLimit(lineLimit)
        .focused($isFocused)
        .disabled(!isEnabled)
        .allowsHitTesting(!isReadOnly)
        .font(theme.typography.body)
        .foregroundStyle(isEnabled ? theme.colors.textPrimary : theme.colors.textMuted)
        .tint(theme.colors.inputCursor)
        .padding(
            .bottom,
            showsCharacterCount && maximumCharacterCount != nil
                ? theme.spacing.xl
                : theme.spacing.none
        )
        .padding(theme.spacing.lg)
        .frame(height: height, alignment: .topLeading)
        .background(
            theme.colors.inputBackground,
            in: RoundedRectangle(cornerRadius: theme.shapes.input)
        )
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.input)
                .stroke(
                    isFocused ? theme.colors.actionPrimary : theme.colors.border,
                    lineWidth: Metrics.borderWidth
                )
        }
        .overlay(alignment: .bottomTrailing) {
            if showsCharacterCount, let maximumCharacterCount {
                Text("\(text.count) / \(maximumCharacterCount)")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textInactive)
                    .padding(theme.spacing.lg)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityLabel(label)
        .accessibilityValue(accessibilityValue)
    }

    private var limitedText: Binding<String> {
        Binding(
            get: { text },
            set: { newValue in
                text = maximumCharacterCount.map {
                    newValue.limited(toCharacterCount: $0)
                } ?? newValue
            }
        )
    }

    private var accessibilityValue: String {
        guard let maximumCharacterCount else { return text }
        return "\(text), \(maximumCharacterCount)자 중 \(text.count)자 입력"
    }
}

extension String {
    func limited(toCharacterCount maximumCount: Int) -> String {
        String(prefix(max(0, maximumCount)))
    }
}

private enum Metrics {
    static let borderWidth: CGFloat = 1
}

#Preview("Text Field") {
    @Previewable @State var text = ""

    VStack(spacing: ChalkakSpacing.lg) {
        ChalkakTextField(
            text: $text,
            label: "사진 설명",
            placeholder: "제목은 선택이에요.",
            maximumCharacterCount: 10,
            height: 148
        )

        ChalkakTextField(
            text: $text,
            label: "사진 제목",
            placeholder: "제목은 선택이지롱.",
            maximumCharacterCount: 50
        )
    }
    .padding(ChalkakSpacing.xl)
}
