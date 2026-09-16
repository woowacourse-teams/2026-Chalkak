import SwiftUI

/// 텍스트 길이를 어떤 단위로 세고 제한할지 정한다.
///
/// 서버 검증 기준과 UI 제한·카운터를 맞추기 위한 값이다. 기본값은 사용자가 눈으로 세는
/// 글자 수(grapheme)이며, 서버가 다른 단위로 검증하는 화면은 그 단위를 골라 맞춘다.
enum ChalkakTextLengthMetric {
    /// 사람이 인식하는 글자(grapheme cluster) 수. `String.count` 기준.
    case characters
    /// Unicode scalar(=code point) 수. 서버가 code point로 길이를 검증할 때 사용한다.
    case unicodeScalars
}

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
    var lengthMetric: ChalkakTextLengthMetric = .characters
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
                Text("\(currentLength) / \(maximumCharacterCount)")
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

    private var currentLength: Int {
        displayedText.length(using: lengthMetric)
    }

    private var accessibilityValue: String {
        guard let maximumCharacterCount else { return displayedText }
        return "\(displayedText), \(maximumCharacterCount)자 중 \(currentLength)자 입력"
    }

    private func normalizedText(_ value: String) -> String {
        guard let maximumCharacterCount else { return value }
        return value.limited(to: maximumCharacterCount, using: lengthMetric)
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
    /// 주어진 기준으로 이 문자열의 길이를 센다.
    func length(using metric: ChalkakTextLengthMetric) -> Int {
        switch metric {
        case .characters:
            return count
        case .unicodeScalars:
            return unicodeScalars.count
        }
    }

    /// 주어진 기준으로 최대 길이를 넘지 않도록 앞에서부터 잘라 반환한다.
    func limited(to maximumCount: Int, using metric: ChalkakTextLengthMetric) -> String {
        let maximum = max(0, maximumCount)
        switch metric {
        case .characters:
            return String(prefix(maximum))
        case .unicodeScalars:
            guard unicodeScalars.count > maximum else { return self }
            return String(String.UnicodeScalarView(unicodeScalars.prefix(maximum)))
        }
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
