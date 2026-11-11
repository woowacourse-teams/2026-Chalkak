import SwiftUI

/// 필터 카드를 누르면 여는 상세 시트 내용. `.sheet(item:) { FilterDetailSheet(...) }`처럼 iOS 기본 시트 안에 넣는다.
///
/// 높이는 두 칸이다.
/// - 작게: 대표 사진(오른쪽 위 별, 오른쪽 아래 제작자 사인), 이름, 설명, 사용 버튼까지만 보이는 높이. 설명 길이에 따라 재서 맞춘다.
/// - 크게(`.large`): 아래로 "필터 제작자가 찍은 사진"(세로 목록), "이 필터로 찍은 사진"(3열)까지 보인다.
///
/// "이 필터 사용해보기" 버튼은 시트 아래에 고정돼 높이를 바꿔도 화면 아래에 남고, 내용은 버튼 위까지만 스크롤돼 마지막 사진이 가려지지 않는다.
/// 끌어내리기·바깥 누르기로 닫히는 것은 시트 기본 동작이다(닫힘 처리는 `.sheet`의 바인딩이 한다).
/// 즐겨찾기 상태는 `isFavorite`으로 받기만 하고, 별·사용 버튼은 `onFavoriteToggle`·`onUseClick`으로 알린다.
struct FilterDetailSheet: View {
    @Environment(\.chalkakTheme) private var theme

    let filter: FilterItem
    let isFavorite: Bool
    let onFavoriteToggle: () -> Void
    let onUseClick: () -> Void

    @State private var isExpanded: Bool
    // 작게 칸 높이의 재료. 처음 그림은 대략값으로 열고, 잰 값이 오면 바꾼다.
    // 함정: `presentationDetents`에 새 높이 값을 넣으면 `withAnimation` 안이어도 시트가 한 프레임 만에 바뀐다(FeedPostEditSheet와 같은 현상).
    // 그래서 대략값이 실제 높이와 크게 다르면 처음 열 때 튀어 보인다. 대략값을 고칠 때 샘플 한 줄 설명 높이에 맞춰 둔다.
    // 근거: 이슈 #939, #1021
    @State private var summaryHeight = Metrics.summaryFallbackHeight
    @State private var useButtonAreaHeight = Metrics.useButtonAreaFallbackHeight
    @State private var bottomSafeAreaHeight: CGFloat = 0

    /// - Parameter startsExpanded: 크게 연 채로 시작한다. Preview에서 크게 모양을 볼 때 쓴다.
    init(
        filter: FilterItem,
        isFavorite: Bool,
        startsExpanded: Bool = false,
        onFavoriteToggle: @escaping () -> Void,
        onUseClick: @escaping () -> Void
    ) {
        self.filter = filter
        self.isFavorite = isFavorite
        self.onFavoriteToggle = onFavoriteToggle
        self.onUseClick = onUseClick
        _isExpanded = State(initialValue: startsExpanded)
    }

    var body: some View {
        let content = ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                summary
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { summaryHeight = $0 }

                photoSections
                    .padding(.top, theme.spacing.xxl)
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, Metrics.topPadding)
            .padding(.bottom, theme.spacing.xl)
        }
        .scrollIndicators(.hidden)
        .scrollBounceBehavior(.basedOnSize)
        // 버튼을 내용의 아래 safe area로 넣어, 시트 높이와 상관없이 시트 바닥에 붙고 스크롤 끝의 사진이 버튼 위에서 멈추게 한다.
        .safeAreaInset(edge: .bottom, spacing: 0) { useButton }
        // 시트 바닥의 홈 인디케이터 영역. 작게 칸 높이에 더한다.
        .onGeometryChange(for: CGFloat.self) { $0.safeAreaInsets.bottom } action: { bottomSafeAreaHeight = $0 }
        .presentationDetents([collapsedDetent, .large], selection: detentSelection)
        .presentationDragIndicator(.visible)

        if ChalkakPlatformAppearance.usesLiquidGlass {
            content
        } else {
            content
                .background(theme.colors.surfaceElevated)
                .presentationCornerRadius(theme.shapes.sheet)
                .presentationBackground(theme.colors.surfaceElevated)
        }
    }

    /// 작게 칸: 손잡이 아래 여백 + 대표 사진·이름·설명 + 사용 버튼 영역 + 홈 인디케이터 영역.
    /// 예: 설명이 한 줄이면 낮고, 여러 줄이면 그만큼 높아진다.
    private var collapsedDetent: PresentationDetent {
        .height(Metrics.topPadding + summaryHeight + useButtonAreaHeight + bottomSafeAreaHeight)
    }

    /// 칸 높이 값이 재는 동안 바뀌어도 "작게/크게" 중 어느 쪽인지만 기억해 선택이 칸 목록에서 벗어나지 않게 한다.
    private var detentSelection: Binding<PresentationDetent> {
        Binding(
            get: { isExpanded ? .large : collapsedDetent },
            set: { isExpanded = $0 == .large }
        )
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 0) {
            FilterPhoto(source: filter.coverImage, aspectRatio: Metrics.coverAspectRatio)
                .overlay(alignment: .bottomTrailing) {
                    Text(filter.creatorSignature)
                        .font(theme.typography.headline)
                        .foregroundStyle(theme.colors.textOnImage)
                        .multilineTextAlignment(.trailing)
                        .padding(theme.spacing.sm)
                        .accessibilityHidden(true)
                }
                .overlay(alignment: .topTrailing) {
                    FilterFavoriteButton(isFavorite: isFavorite, onToggle: onFavoriteToggle)
                        .padding(theme.spacing.xs)
                }

            Text(filter.name)
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.top, theme.spacing.lg)
                .accessibilityAddTraits(.isHeader)

            Text(filter.description)
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, theme.spacing.sm)
        }
    }

    private var photoSections: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle("필터 제작자가 찍은 사진")

            VStack(spacing: theme.spacing.sm) {
                ForEach(Array(filter.creatorPhotos.enumerated()), id: \.offset) { _, source in
                    FilterPhoto(source: source, aspectRatio: Metrics.creatorPhotoAspectRatio)
                }
            }
            .padding(.top, theme.spacing.md)

            sectionTitle("이 필터로 찍은 사진")
                .padding(.top, theme.spacing.xl)

            HStack(spacing: theme.spacing.sm) {
                ForEach(Array(filter.usedPhotos.enumerated()), id: \.offset) { _, source in
                    FilterPhoto(source: source, aspectRatio: Metrics.usedPhotoAspectRatio)
                }
            }
            .padding(.top, theme.spacing.md)
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(theme.typography.headline)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityAddTraits(.isHeader)
    }

    private var useButton: some View {
        ChalkakButton(title: "이 필터 사용해보기", action: onUseClick, fillsWidth: true)
            .accessibilityIdentifier("filter.use")
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, theme.spacing.xl)
            .padding(.bottom, theme.spacing.lg)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { useButtonAreaHeight = $0 }
    }
}

private enum Metrics {
    // 시트 손잡이 아래 첫 내용까지의 여백.
    static let topPadding: CGFloat = 24
    // 시안의 대표 사진·제작자 사진은 가로로 긴 2.2:1, 이 필터로 찍은 사진은 정사각형이다.
    static let coverAspectRatio: CGFloat = 2.2
    static let creatorPhotoAspectRatio: CGFloat = 2.2
    static let usedPhotoAspectRatio: CGFloat = 1
    // 재기 전 첫 그림의 대략값(대표 사진 약 160 + 이름·설명 한 줄, 버튼 56 + 위아래 여백).
    static let summaryFallbackHeight: CGFloat = 240
    static let useButtonAreaFallbackHeight: CGFloat = 96
}

// MARK: - Preview

private struct FilterDetailSheetPreview: View {
    let filter: FilterItem
    let startsExpanded: Bool
    @State var isFavorite: Bool

    var body: some View {
        Color.clear
            .sheet(isPresented: .constant(true)) {
                FilterDetailSheet(
                    filter: filter,
                    isFavorite: isFavorite,
                    startsExpanded: startsExpanded,
                    onFavoriteToggle: { isFavorite.toggle() },
                    onUseClick: {}
                )
                .chalkakTheme(.light)
            }
            .chalkakTheme(.light)
    }
}

#Preview("필터 시트 작게") {
    FilterDetailSheetPreview(filter: FilterItem.samples[0], startsExpanded: false, isFavorite: false)
}

#Preview("필터 시트 크게(즐겨찾기 켬)") {
    FilterDetailSheetPreview(filter: FilterItem.samples[0], startsExpanded: true, isFavorite: true)
}

#Preview("필터 시트 작게(긴 설명)") {
    let sample = FilterItem.samples[0]
    FilterDetailSheetPreview(
        filter: FilterItem(
            id: sample.id,
            name: sample.name,
            description: Array(repeating: "언제 언제 쓰면 예뻐요", count: 12).joined(separator: " "),
            creatorSignature: sample.creatorSignature,
            coverImage: sample.coverImage,
            creatorPhotos: sample.creatorPhotos,
            usedPhotos: sample.usedPhotos
        ),
        startsExpanded: false,
        isFavorite: false
    )
}
