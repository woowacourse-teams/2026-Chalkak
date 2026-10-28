import SwiftUI

/// 작가 사진 목록의 탑바. 왼쪽 뒤로 가기는 고정이고, 가운데 작가 사인은 스크롤에 따라 크기가 바뀐다.
/// 처음(`collapseProgress` 0)에는 사인이 탑바 아래까지 크게 걸쳐 있고,
/// 끝까지 접히면(1) 뒤로 가기와 같은 줄 가운데에 작게 자리 잡는다.
/// 탑바 자체의 높이는 `height`로 고정이며, 펼친 사인은 그 아래로 넘쳐 그려진다(호출부가 그 자리를 비워 둔다).
struct AuthorPostsTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    // 접힌 탑바 높이. 뒤로 가기 버튼과 위아래 여백(md)을 더한 값으로 피드 탑바와 같다.
    static let height: CGFloat = ChalkakNavigationButton.diameter + 2 * ChalkakSpacing.md
    // 펼친 사인의 아래 끝(탑바 위 끝 기준). 호출부는 이 아래에 그리드 위 설명을 둔다.
    static let expandedSignatureBottom: CGFloat = Metrics.expandedSignatureTop + Metrics.expandedSignatureSize.height

    // nil이면 사인을 그리지 않는다(없거나 탈퇴한 작가).
    let signatureImageSource: ChalkakImageSource?
    let isMine: Bool
    // 0: 펼침(큰 사인), 1: 접힘(작은 사인). 범위 밖 값은 잘라서 쓴다.
    var collapseProgress: CGFloat = 0
    let onBack: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            HStack(spacing: 0) {
                ChalkakNavigationButton(kind: .back, action: onBack)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)

            if let signatureImageSource {
                signature(signatureImageSource)
            }
        }
        .frame(height: Self.height, alignment: .top)
    }

    // 사인은 흰색 PNG로 저장돼 밝은 배경에서 보이지 않는다. 사인의 모양(알파)만 남기고 아이콘 색을 입힌다.
    private func signature(_ source: ChalkakImageSource) -> some View {
        let progress = min(max(collapseProgress, 0), 1)
        let size = CGSize(
            width: Metrics.expandedSignatureSize.width
                + (Metrics.collapsedSignatureSize.width - Metrics.expandedSignatureSize.width) * progress,
            height: Metrics.expandedSignatureSize.height
                + (Metrics.collapsedSignatureSize.height - Metrics.expandedSignatureSize.height) * progress
        )
        let top = Metrics.expandedSignatureTop + (Metrics.collapsedSignatureTop - Metrics.expandedSignatureTop) * progress

        return theme.colors.iconPrimary
            .mask {
                ChalkakImage(source: source, contentDescription: nil, contentMode: .fit, showsLoadingSkeleton: false)
            }
            .frame(width: size.width, height: size.height)
            .padding(.top, top)
            .accessibilityElement()
            .accessibilityLabel(isMine ? "내 사인" : "작성자 사인")
            .accessibilityAddTraits(.isImage)
    }
}

private enum Metrics {
    // 접힌 사인. 뒤로 가기 버튼 높이 안에 들어오며 카드·피드의 사인 칸과 같은 4:3 비율이다.
    static let collapsedSignatureSize = CGSize(width: 64, height: 48)
    static let collapsedSignatureTop: CGFloat = ChalkakSpacing.md
    // 펼친 사인. 전시 화면에서 필터를 뺀 헤더 높이 안에 설명과 함께 들어오는 크기다.
    static let expandedSignatureSize = CGSize(width: 120, height: 90)
    static let expandedSignatureTop: CGFloat = 19
}

#Preview("Author Posts Top Bar", traits: .sizeThatFitsLayout) {
    VStack(spacing: ChalkakSpacing.xxl) {
        AuthorPostsTopBar(signatureImageSource: .asset("preview_signature"), isMine: false, onBack: {})
            .padding(.bottom, AuthorPostsTopBar.expandedSignatureBottom - AuthorPostsTopBar.height)
        AuthorPostsTopBar(
            signatureImageSource: .asset("preview_signature"),
            isMine: false,
            collapseProgress: 1,
            onBack: {}
        )
        AuthorPostsTopBar(signatureImageSource: nil, isMine: false, onBack: {})
    }
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
