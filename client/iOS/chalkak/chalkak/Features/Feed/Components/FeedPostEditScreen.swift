import SwiftUI

/// 피드 상세의 '수정'으로 여는 게시물 수정 화면.
///
/// 전시하기 글 쓰기(PhotoUploadWritingScreen)와 같은 배치(사진 썸네일 옆 제목, 그 아래 '이야기' 칸, 아래 고정 버튼)로
/// 제목과 설명을 함께 고친다. 입력값은 `edit`으로 받기만 하고, 바뀐 글은 `onTitleChange`·`onDescriptionChange`로 뷰모델에 올린다.
/// 저장 중(`isSaving`)에는 입력과 닫기를 막는다. 응답이 오기 전에 닫으면 결과를 볼 곳이 없기 때문이다.
///
/// 예: 제목 "제목", 설명 "이야기"인 게시물이면 두 칸에 그 글이 채워진 채 열린다.
struct FeedPostEditScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let edit: FeedPostEditState
    let imageSource: ChalkakImageSource
    let isSaving: Bool
    let onTitleChange: (String) -> Void
    let onDescriptionChange: (String) -> Void
    let onSave: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .bottom, spacing: theme.spacing.lg) {
                        ChalkakImage(
                            source: imageSource,
                            contentDescription: "수정할 사진",
                            maxPixelWidth: Metrics.thumbnailSize * 3
                        )
                        .frame(width: Metrics.thumbnailSize, height: Metrics.thumbnailSize)
                        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))

                        VStack(alignment: .leading, spacing: theme.spacing.xs) {
                            sectionLabel("제목")
                            ChalkakTextField(
                                text: Binding(get: { edit.title }, set: onTitleChange),
                                label: "작품 제목",
                                placeholder: "사진의 제목을 적어 주세요. (선택)",
                                isEnabled: !isSaving,
                                lineLimit: 1...1,
                                textFont: theme.typography.headline,
                                maximumCharacterCount: Constants.titleMaxLength,
                                // 서버가 제목·설명 길이를 code point로 검증하므로(POST-005) 같은 단위로 센다.
                                lengthMetric: .unicodeScalars
                            )
                            .accessibilityIdentifier("feed.postEdit.title")
                        }
                    }

                    Divider()
                        .overlay(theme.colors.border)
                        .padding(.vertical, theme.spacing.lg)

                    sectionLabel("이야기")
                        .padding(.bottom, theme.spacing.sm)
                    ChalkakTextField(
                        text: Binding(get: { edit.description }, set: onDescriptionChange),
                        label: "작품 이야기",
                        placeholder: "사진에 담긴 이야기를 적어 주세요. (선택)",
                        isEnabled: !isSaving,
                        lineLimit: Constants.descriptionLineLimit,
                        textFont: theme.typography.body,
                        maximumCharacterCount: Constants.descriptionMaxLength,
                        lengthMetric: .unicodeScalars
                    )
                    .accessibilityIdentifier("feed.postEdit.description")
                }
                .padding(.horizontal, Metrics.contentHorizontalPadding)
                .padding(.top, theme.spacing.lg)
                .padding(.bottom, theme.spacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
            // 버튼 자리를 스크롤 영역에 붙여야 키보드가 올라와도 이야기 칸이 버튼 뒤로 숨지 않는다.
            // 바깥 VStack에 붙이면 버튼이 칸 위에 겹친다(#938 화면 확인에서 발견).
            .safeAreaInset(edge: .bottom, spacing: 0) {
                ChalkakButton(
                    title: isSaving ? "저장 중..." : "저장",
                    action: onSave,
                    isEnabled: !isSaving,
                    fillsWidth: true
                )
                .accessibilityIdentifier("feed.postEdit.save")
                .padding(.horizontal, Metrics.buttonHorizontalPadding)
                .padding(.top, theme.spacing.sm)
                .padding(.bottom, Metrics.buttonBottomPadding)
                // 스크롤된 이야기 칸이 버튼 양옆으로 비치지 않게 버튼 줄에 화면 바탕을 깐다.
                .background(theme.colors.background)
            }
        }
        .background(theme.colors.background)
        // 저장 중에는 아래로 끌어 닫지 못하게 한다.
        .interactiveDismissDisabled(isSaving)
    }

    private var topBar: some View {
        ZStack {
            Text("게시물 수정")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
            HStack {
                ChalkakNavigationButton(kind: .back, action: { if !isSaving { onClose() } })
                Spacer()
            }
        }
        .padding(.horizontal, theme.spacing.lg)
        .padding(.vertical, theme.spacing.md)
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(theme.typography.caption)
            .fontWeight(.semibold)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityHidden(true)
    }

    private enum Constants {
        // 서버 입력 규칙(POST-005)과 같은 최대 길이. 전시하기의 이야기는 150자지만 수정은 서버 기준 300자로 받는다(#938 계획).
        static let titleMaxLength = 10
        static let descriptionMaxLength = 300
        static let descriptionLineLimit = 8...16
    }
}

// 전시하기 글 쓰기 화면과 같은 배치를 맞추기 위한 고정값이다.
private enum Metrics {
    static let contentHorizontalPadding: CGFloat = 22
    static let buttonHorizontalPadding: CGFloat = 26
    static let buttonBottomPadding: CGFloat = 26
    static let thumbnailSize: CGFloat = 84
}

#Preview("Feed Post Edit") {
    FeedPostEditScreen(
        edit: FeedPostEditState(title: "늦은 오후의 골목", description: "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요."),
        imageSource: .asset("preview_photo"),
        isSaving: false,
        onTitleChange: { _ in },
        onDescriptionChange: { _ in },
        onSave: {},
        onClose: {}
    )
}
