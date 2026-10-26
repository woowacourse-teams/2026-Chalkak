import SwiftUI

/// 작가 사진 목록의 탑바. 뒤로 가기 / 가운데 작가 사인 / 오른쪽은 뒤로 가기와 같은 폭의 빈 자리라
/// 사인이 화면 가운데에 온다. Android와 같은 모양이며 스크롤해도 접히지 않는다.
struct AuthorPostsTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    // nil이면 사인을 그리지 않는다(없거나 탈퇴한 작가).
    let signatureImageSource: ChalkakImageSource?
    let isMine: Bool
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            ChalkakNavigationButton(kind: .back, action: onBack)

            Spacer(minLength: theme.spacing.sm)

            if let signatureImageSource {
                signature(signatureImageSource)
            }

            Spacer(minLength: theme.spacing.sm)

            // 사인을 가운데에 두기 위한 오른쪽 여백.
            Color.clear
                .frame(width: ChalkakNavigationButton.diameter, height: ChalkakNavigationButton.diameter)
                .accessibilityHidden(true)
        }
    }

    // 사인은 흰색 PNG로 저장돼 밝은 배경에서 보이지 않는다. 사인의 모양(알파)만 남기고 아이콘 색을 입힌다.
    private func signature(_ source: ChalkakImageSource) -> some View {
        theme.colors.iconPrimary
            .mask {
                ChalkakImage(source: source, contentDescription: nil, contentMode: .fit, showsLoadingSkeleton: false)
            }
            .frame(width: Metrics.signatureSize.width, height: Metrics.signatureSize.height)
            .accessibilityElement()
            .accessibilityLabel(isMine ? "내 사인" : "작성자 사인")
            .accessibilityAddTraits(.isImage)
    }
}

private enum Metrics {
    // 뒤로 가기 버튼 높이 안에 들어오는 사인 크기. 카드·피드의 사인 칸과 같은 4:3 비율이다.
    static let signatureSize = CGSize(width: 64, height: 48)
}

#Preview("Author Posts Top Bar", traits: .sizeThatFitsLayout) {
    VStack {
        AuthorPostsTopBar(signatureImageSource: .asset("preview_signature"), isMine: false, onBack: {})
        AuthorPostsTopBar(signatureImageSource: nil, isMine: false, onBack: {})
    }
    .padding(.horizontal, ChalkakSpacing.lg)
    .padding(.vertical, ChalkakSpacing.md)
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
