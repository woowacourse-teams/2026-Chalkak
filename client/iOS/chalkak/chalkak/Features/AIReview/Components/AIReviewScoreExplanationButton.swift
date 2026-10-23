import SwiftUI

struct AIReviewScoreExplanationButton: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var isPresented = false

    var body: some View {
        Button("찰캌 AI는 어떤 기준으로 점수를 내나요?") { isPresented = true }
            .font(theme.typography.caption)
            .underline()
            .foregroundStyle(theme.colors.textSecondary)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .trailing)
            .alert("찰칵 AI의 점수 기준", isPresented: $isPresented) {
                Button("확인", role: .cancel) {}
            } message: {
                Text("찰캌에서는 Nikon, PPA(Professional Photographers of America), Canon 등의 기준으로 학습된 AI를 통해 피드백을 해줘요!")
            }
    }
}
