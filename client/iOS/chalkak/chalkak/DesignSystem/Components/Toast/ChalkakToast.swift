import SwiftUI

private struct ChalkakShowToastKey: EnvironmentKey {
    static let defaultValue: (String) -> Void = { _ in }
}

extension EnvironmentValues {
    var chalkakShowToast: (String) -> Void {
        get { self[ChalkakShowToastKey.self] }
        set { self[ChalkakShowToastKey.self] = newValue }
    }
}

struct ChalkakToast: View {
    @Environment(\.chalkakTheme) private var theme
    let message: String

    var body: some View {
        Text(message)
            .font(theme.typography.subheadline)
            .foregroundStyle(theme.colors.onActionPrimary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)
            .background(theme.colors.actionPrimary, in: Capsule())
            .accessibilityLabel(message)
            .accessibilityIdentifier("chalkak.toast")
    }
}

private struct ChalkakToastOverlay: ViewModifier {
    @Environment(\.chalkakTheme) private var theme
    let message: String?

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message {
                ChalkakToast(message: message)
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.bottom, ToastMetrics.bottomPadding)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
        }
    }
}

private struct ChalkakToastHost: ViewModifier {
    @State private var message: String?
    @State private var dismissalID = UUID()

    func body(content: Content) -> some View {
        content
            .environment(\.chalkakShowToast, showMessage)
            .chalkakToast(message)
            .task(id: dismissalID) {
                guard message != nil else { return }
                do {
                    try await Task.sleep(for: .seconds(2.5))
                    withAnimation(.snappy) { message = nil }
                } catch is CancellationError {
                    return
                } catch {
                    return
                }
            }
    }

    private func showMessage(_ text: String) {
        withAnimation(.snappy) {
            message = text
            dismissalID = UUID()
        }
    }
}

extension View {
    func chalkakToast(_ message: String?) -> some View {
        modifier(ChalkakToastOverlay(message: message))
    }

    // Attach to a screen or presentation root, outside any bottom-bar safeAreaInset.
    func chalkakToastHost() -> some View {
        modifier(ChalkakToastHost())
    }
}

private enum ToastMetrics {
    static let bottomPadding: CGFloat = 32
}

#Preview {
    ChalkakToast(message: "사인 반영까지 시간이 좀 걸릴 수 있어요!")
        .chalkakTheme(.light)
}
