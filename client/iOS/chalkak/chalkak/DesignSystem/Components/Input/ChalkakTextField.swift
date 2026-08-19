import SwiftUI

struct ChalkakTextField: View {
    @Environment(\.chalkakColors) private var colors
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
                .foregroundStyle(colors.textInactive),
            axis: lineLimit.upperBound == 1 ? .horizontal : .vertical
        )
        .labelsHidden()
        .lineLimit(lineLimit)
        .focused($isFocused)
        .disabled(!isEnabled)
        .allowsHitTesting(!isReadOnly)
        .font(ChalkakTypography.body)
        .foregroundStyle(isEnabled ? colors.textPrimary : colors.textMuted)
        .tint(colors.actionPrimary)
        .padding(
            .bottom,
            showsCharacterCount && maximumCharacterCount != nil
                ? ChalkakSpacing.xl
                : ChalkakSpacing.none
        )
        .padding(ChalkakSpacing.lg)
        .frame(height: height, alignment: .topLeading)
        .background(
            colors.inputBackground,
            in: RoundedRectangle(cornerRadius: ChalkakShape.input)
        )
        .overlay {
            RoundedRectangle(cornerRadius: ChalkakShape.input)
                .stroke(
                    isFocused ? colors.actionPrimary : colors.border,
                    lineWidth: Metrics.borderWidth
                )
        }
        .overlay(alignment: .bottomTrailing) {
            if showsCharacterCount, let maximumCharacterCount {
                Text("\(text.count) / \(maximumCharacterCount)")
                    .font(ChalkakTypography.subheadline)
                    .foregroundStyle(colors.textInactive)
                    .padding(ChalkakSpacing.lg)
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
            placeholder: "한 줄은 선택이에요.",
            maximumCharacterCount: 50,
            height: 148
        )

        ChalkakTextField(
            text: .constant("수정할 수 없는 설명"),
            label: "사진 설명",
            isEnabled: false,
            maximumCharacterCount: 50
        )
    }
    .padding(ChalkakSpacing.xl)
}
