import Testing
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
}
