import CoreGraphics
import SwiftUI
import Testing
import UIKit
@testable import chalkak

struct ChalkakTextFieldTests {
    @Test("문자 수보다 짧은 입력은 유지한다")
    func preservesTextWithinLimit() {
        #expect("찰칵".limited(toCharacterCount: 5) == "찰칵")
    }

    @Test("문자 수 제한을 초과한 입력을 자른다")
    func truncatesTextBeyondLimit() {
        #expect("찰칵사진".limited(toCharacterCount: 2) == "찰칵")
    }

    @Test("이모지와 조합 문자를 하나의 문자로 계산한다")
    func preservesExtendedGraphemeClusters() {
        let text = "👨‍👩‍👧‍👦e\u{301}사진"

        #expect(text.limited(toCharacterCount: 2) == "👨‍👩‍👧‍👦e\u{301}")
    }

    @Test("0 이하의 제한은 빈 문자열을 반환한다")
    func returnsEmptyTextForNonPositiveLimit() {
        #expect("찰칵".limited(toCharacterCount: 0).isEmpty)
        #expect("찰칵".limited(toCharacterCount: -1).isEmpty)
    }

    @MainActor
    @Test("명시한 높이를 테두리와 배경에 반영한다")
    func honorsExplicitHeight() {
        let view = ChalkakTextField(
            text: .constant(""),
            label: "사진 설명",
            maximumCharacterCount: 50,
            height: 148
        )
        let hostingController = UIHostingController(
            rootView: view.frame(width: 320)
        )

        let size = hostingController.sizeThatFits(
            in: CGSize(width: 320, height: 1_000)
        )

        #expect(size.height == 148)
    }
}

@MainActor
struct ChalkakTextFieldInputTests {
    @Test("입력 중 최대 글자 수를 초과한 문자는 화면에 남기지 않는다")
    func preventsInputBeyondMaximumCharacterCount() {
        let model = TextModel()
        let view = ChalkakTextField(
            text: Binding(
                get: { model.text },
                set: { model.text = $0 }
            ),
            label: "사진 설명",
            maximumCharacterCount: 10
        )
        let hostingController = UIHostingController(rootView: view)
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first
        else {
            Issue.record("윈도우 씬을 찾지 못했습니다")
            return
        }
        let window = UIWindow(windowScene: windowScene)
        window.frame = CGRect(x: 0, y: 0, width: 320, height: 148)
        defer { window.isHidden = true }
        window.rootViewController = hostingController
        window.makeKeyAndVisible()
        hostingController.view.frame = window.bounds
        hostingController.view.layoutIfNeeded()

        guard let inputView = textInput(in: hostingController.view),
              let input = inputView as? UIKeyInput
        else {
            Issue.record("텍스트 입력 뷰를 찾지 못했습니다")
            return
        }

        inputView.becomeFirstResponder()
        for _ in 0..<11 {
            input.insertText("a")
        }
        RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.1))

        #expect(model.text == String(repeating: "a", count: 10))
        #expect(inputText(in: hostingController.view) == String(repeating: "a", count: 10))
    }

    private func textInput(in view: UIView) -> UIView? {
        if view is UITextView || view is UITextField {
            return view
        }
        for subview in view.subviews {
            if let inputView = textInput(in: subview) {
                return inputView
            }
        }
        return nil
    }

    private func inputText(in view: UIView) -> String? {
        if let textView = view as? UITextView {
            return textView.text
        }
        if let textField = view as? UITextField {
            return textField.text
        }
        for subview in view.subviews {
            if let text = inputText(in: subview) {
                return text
            }
        }
        return nil
    }

    @MainActor
    private final class TextModel {
        var text = ""
    }
}

struct ChalkakThemeTests {
    @Test("Android와 동일한 spacing 토큰을 제공한다")
    func providesSharedSpacingTokens() {
        let spacing = ChalkakTheme.light.spacing

        #expect(spacing.none == 0)
        #expect(spacing.xs == 4)
        #expect(spacing.sm == 8)
        #expect(spacing.md == 12)
        #expect(spacing.lg == 16)
        #expect(spacing.xl == 24)
        #expect(spacing.xxl == 40)
        #expect(spacing.screenHorizontal == 25)
    }

    @Test("Android와 동일한 shape 토큰을 제공한다")
    func providesSharedShapeTokens() {
        let shapes = ChalkakTheme.light.shapes

        #expect(shapes.small == 7)
        #expect(shapes.button == 12)
        #expect(shapes.input == 14)
        #expect(shapes.photoCard == 12)
        #expect(shapes.large == 16)
        #expect(shapes.xlarge == 18)
        #expect(shapes.sheet == 28)
    }

    @Test("테마가 색상·타이포그래피·spacing·shape를 함께 제공한다")
    func providesCompleteLightTheme() {
        let theme = ChalkakTheme.light

        _ = theme.colors
        _ = theme.typography
        _ = theme.spacing
        _ = theme.shapes

        #expect(theme.spacing.screenHorizontal == 25)
    }
}
