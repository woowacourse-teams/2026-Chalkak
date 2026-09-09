import SwiftUI

struct ChalkakTextField: View {
    @Environment(\.chalkakTheme) private var theme
    @FocusState private var isFocused: Bool
    @State private var inputText: String?

    @Binding var text: String
    let label: String
    var placeholder = ""
    var isEnabled = true
    var isReadOnly = false
    var lineLimit: ClosedRange<Int> = 1...5
    var textFont: Font? = nil
    var maximumCharacterCount: Int?
    var showsCharacterCount = true
    var height: CGFloat?
    var onFocusChange: ((Bool) -> Void)? = nil

    var body: some View {
        TextField(
            label,
            text: inputBinding,
            prompt: Text(placeholder)
                .foregroundStyle(theme.colors.textInactive),
            axis: lineLimit.upperBound == 1 ? .horizontal : .vertical
        )
        .labelsHidden()
        .lineLimit(lineLimit)
        .focused($isFocused)
        .disabled(!isEnabled)
        .allowsHitTesting(!isReadOnly)
        .font(textFont ?? theme.typography.body)
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
                Text("\(displayedText.count) / \(maximumCharacterCount)")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textInactive)
                    .padding(theme.spacing.lg)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityLabel(label)
        .accessibilityValue(accessibilityValue)
        .onAppear(perform: synchronizeInputText)
        .onChange(of: inputText) { _, newValue in
            guard let newValue else { return }
            let normalizedValue = normalizedText(newValue)
            if inputText != normalizedValue {
                inputText = normalizedValue
            }
            if text != normalizedValue {
                text = normalizedValue
            }
        }
        .onChange(of: text) { _, newValue in
            let normalizedValue = normalizedText(newValue)
            if inputText != normalizedValue {
                inputText = normalizedValue
            }
            if text != normalizedValue {
                text = normalizedValue
            }
        }
        .onChange(of: isFocused) { _, isFocused in
            onFocusChange?(isFocused)
        }
    }

    private var inputBinding: Binding<String> {
        Binding(
            get: { normalizedText(inputText ?? text) },
            set: { inputText = $0 }
        )
    }

    private var displayedText: String {
        normalizedText(inputText ?? text)
    }

    private var accessibilityValue: String {
        guard let maximumCharacterCount else { return displayedText }
        return "\(displayedText), \(maximumCharacterCount)자 중 \(displayedText.count)자 입력"
    }

    private func normalizedText(_ value: String) -> String {
        maximumCharacterCount.map(value.limited(toCharacterCount:)) ?? value
    }

    private func synchronizeInputText() {
        let normalizedValue = normalizedText(text)
        if inputText != normalizedValue {
            inputText = normalizedValue
        }
        if text != normalizedValue {
            text = normalizedValue
        }
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
