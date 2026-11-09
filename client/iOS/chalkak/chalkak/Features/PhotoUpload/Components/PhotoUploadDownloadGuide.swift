import SwiftUI

/// 고른 사진을 불러오는 동안 띄우는 안내. iCloud에만 있는 사진은 내려받느라 시간이 걸린다.
/// 기기에 있는 사진은 바로 불러오므로, 잠깐 기다려도 끝나지 않을 때만 보인다.
/// 시스템 알림처럼 제목, 진행률, 취소 버튼만 두고, 리퀴드 글라스를 쓸 수 있으면 글라스로 그린다.
struct PhotoUploadDownloadGuide: View {
    @Environment(\.chalkakTheme) private var theme
    let progress: Double?
    let onCancel: () -> Void

    @State private var isVisible = false

    var body: some View {
        ZStack {
            if isVisible {
                theme.colors.scrim
                    .ignoresSafeArea()
                    .transition(.opacity)

                alert
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .transition(.opacity.combined(with: .scale(scale: Metrics.appearScale)))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // 안내가 뜨기 전에도 사진을 다시 고르는 등의 입력을 막는다.
        .contentShape(Rectangle())
        .task {
            try? await Task.sleep(for: Metrics.appearanceDelay)
            withAnimation(.easeOut) { isVisible = true }
        }
    }

    private var alert: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xl) {
            Text("iCloud에서 사진을 내려받는 중")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)

            // 진행률을 알 수 없으면 빈 막대로 둔다.
            ProgressView(value: progress ?? 0)
                .progressViewStyle(.linear)
                .tint(theme.colors.actionPrimary)
                .accessibilityLabel("사진 내려받는 중")
                .accessibilityValue(progress.map { "\(Int($0 * 100))퍼센트" } ?? "")

            cancelButton
        }
        .padding(Metrics.alertPadding)
        .frame(maxWidth: Metrics.alertMaxWidth)
        .modifier(DownloadGuideAlertBackground())
        // 앱 화면은 다크 모드에서도 밝은 바탕이므로, 글라스와 시스템 컨트롤도 밝은 모양으로 고정한다.
        .environment(\.colorScheme, .light)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("photoUpload.download")
    }

    // 시스템 알림의 버튼처럼 시스템 파란색을 쓴다.
    private var cancelButton: some View {
        Button(action: onCancel) {
            Text("취소")
                .font(theme.typography.callout)
                .frame(maxWidth: .infinity)
        }
        .modifier(DownloadGuideCancelButtonStyle())
        .controlSize(.large)
        .tint(.blue)
        .accessibilityIdentifier("photoUpload.download.cancel")
    }
}

private struct DownloadGuideCancelButtonStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            content.buttonStyle(.glassProminent)
        } else {
            content
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
        }
    }
}

private struct DownloadGuideAlertBackground: ViewModifier {
    @Environment(\.chalkakTheme) private var theme

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *), ChalkakPlatformAppearance.usesLiquidGlass {
            content.glassEffect(.regular, in: RoundedRectangle(cornerRadius: Metrics.glassCornerRadius))
        } else {
            content.background(theme.colors.surfaceElevated, in: RoundedRectangle(cornerRadius: theme.shapes.large))
        }
    }
}

private enum Metrics {
    static let appearanceDelay: Duration = .milliseconds(400)
    static let appearScale: CGFloat = 0.96
    static let alertPadding: CGFloat = 24
    static let alertMaxWidth: CGFloat = 340
    /// iOS 26 시스템 알림의 모서리와 맞춘다.
    static let glassCornerRadius: CGFloat = 34
}

#Preview("Download Guide Progress") {
    PhotoUploadDownloadGuide(progress: 0.42, onCancel: {})
        .chalkakTheme(.light)
}

#Preview("Download Guide Unknown Progress") {
    PhotoUploadDownloadGuide(progress: nil, onCancel: {})
        .chalkakTheme(.light)
}
