import SwiftUI

struct HomeHeroCard: View {
    @Environment(\.chalkakTheme) private var theme
    let hero: HomeHeroState
    let topic: String
    var onOpenPhotoUpload: () -> Void
    var onRetry: () -> Void
    var onLogin: () -> Void = {}
    // 내 사진은 피드처럼 원본 비율(height / width)로 보여 준다. 비율을 알기 전과 다른 상태는 기본 비율을 쓴다.
    @State private var photoRatio: CGFloat?

    var body: some View {
        ZStack {
            switch hero {
            case .loading:
                HeroStatusContent(text: "사진을 확인하고 있어요") {
                    ProgressView()
                        .tint(theme.colors.actionPrimary)
                        .controlSize(.regular)
                }
            case .empty:
                HeroActionContent(
                    title: "아직 \"\(displayTopic)\" 사진을 전시하지 않았어요.\n작가님의 시선을 공유해주세요!",
                    actionTitle: "사진 전시하기",
                    accessibilityLabel: "\(displayTopic) 사진 전시하기",
                    showsAddIcon: true,
                    action: onOpenPhotoUpload
                )
            case .loginRequired:
                HeroActionContent(
                    title: "사진을 전시하려면 로그인이 필요해요",
                    actionTitle: "로그인하기",
                    accessibilityLabel: "로그인하기",
                    action: onLogin
                )
            case let .rejected(canResubmit):
                if canResubmit {
                    HeroActionContent(
                        title: "사진이 반려되었어요",
                        subtitle: "참여 기간 안에 다시 제출할 수 있어요",
                        actionTitle: "다시 제출하기",
                        accessibilityLabel: "사진 다시 제출하기",
                        showsAddIcon: true,
                        action: onOpenPhotoUpload
                    )
                } else {
                    HeroStatusContent(text: "사진이 반려되었어요\n이 주제의 참여 기간이 종료되었어요")
                }
            case .noOpenTopic:
                HeroStatusContent(text: "지금은 참여할 수 있는 주제가 없어요")
            case .processing:
                HeroStatusContent(text: "사진을 처리하고 있어요")
            case .error:
                HeroStatusContent(text: "사진 상태를 불러오지 못했어요") {
                    Button(action: onRetry) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                            .foregroundStyle(theme.colors.onActionPrimary)
                            .frame(width: Metrics.retryButtonSize, height: Metrics.retryButtonSize)
                            .background(theme.colors.actionPrimary, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("홈 다시 시도")
                }
            case let .photo(photo):
                PhotoHeroContent(photo: photo)
                    .task(id: photo.imageSource) {
                        photoRatio = await ImageRatioLoader.ratio(for: photo.imageSource)
                    }
            }
        }
        .aspectRatio(aspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
        .accessibilityIdentifier(accessibilityIdentifier)
    }

    private var aspectRatio: CGFloat {
        switch hero {
        case .empty, .loginRequired, .rejected(canResubmit: true):
            return Metrics.emptyAspectRatio
        case .photo:
            guard let photoRatio, photoRatio > 0 else { return Metrics.aspectRatio }
            return 1 / photoRatio
        default:
            return Metrics.aspectRatio
        }
    }

    private var accessibilityIdentifier: String {
        switch hero {
        case .loading: "home-loading"
        case .empty: "home-empty"
        case .loginRequired: "home-login-required"
        case .rejected: "home-rejected"
        default: "home-hero"
        }
    }

    private var displayTopic: String {
        topic.isEmpty ? "오늘의 주제" : topic
    }
}

private struct PhotoHeroContent: View {
    @Environment(\.chalkakTheme) private var theme
    let photo: HomeHeroPhoto

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if let signatureSource = photo.signatureSource {
                ChalkakSignedImage(
                    imageSource: photo.imageSource,
                    signatureSource: signatureSource,
                    contentDescription: nil,
                    signatureSize: Metrics.signatureSize,
                    thumbnailImageSource: photo.thumbnailSource
                )
            } else {
                ChalkakImage(
                    source: photo.imageSource,
                    contentDescription: nil,
                    thumbnailSource: photo.thumbnailSource
                )
            }

            if photo.isPending {
                Text("검수 대기")
                    .font(theme.typography.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(theme.colors.textOnImage)
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.xs)
                    .background(theme.colors.scrim, in: Capsule())
                    .padding(theme.spacing.md)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.surfaceElevated)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isImage)
        .accessibilityLabel(photo.isPending ? "오늘 제출한 사진, 검수 대기" : "오늘 제출한 사진")
    }
}

private struct HeroActionContent: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    var subtitle: String?
    let actionTitle: String
    let accessibilityLabel: String
    var showsAddIcon = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: theme.spacing.xl) {
                VStack(spacing: theme.spacing.xs) {
                    Text(title)
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textPrimary)
                        .multilineTextAlignment(.center)

                    if let subtitle {
                        Text(subtitle)
                            .font(theme.typography.footnote)
                            .foregroundStyle(theme.colors.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.horizontal, theme.spacing.lg)

                HStack(spacing: theme.spacing.sm) {
                    if showsAddIcon {
                        Image(systemName: "plus")
                            .font(.system(size: Metrics.addIconSize, weight: .semibold))
                            .accessibilityHidden(true)
                    }
                    Text(actionTitle)
                        .font(theme.typography.subheadline)
                }
                .foregroundStyle(theme.colors.onActionPrimary)
                .padding(.horizontal, theme.spacing.lg)
                .padding(.vertical, theme.spacing.md)
                .background(theme.colors.actionPrimary, in: RoundedRectangle(cornerRadius: theme.shapes.button))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.colors.surface)
            .overlay {
                RoundedRectangle(cornerRadius: theme.shapes.photoCard)
                    .strokeBorder(theme.colors.border, lineWidth: Metrics.borderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }
}

private struct HeroStatusContent<Action: View>: View {
    @Environment(\.chalkakTheme) private var theme
    let text: String
    @ViewBuilder var action: () -> Action

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text(text)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            action()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.surface)
        .overlay {
            RoundedRectangle(cornerRadius: theme.shapes.photoCard)
                .strokeBorder(theme.colors.border, lineWidth: Metrics.borderWidth)
        }
    }
}

private extension HeroStatusContent where Action == EmptyView {
    init(text: String) {
        self.text = text
        self.action = { EmptyView() }
    }
}

private enum Metrics {
    static let aspectRatio: CGFloat = 5 / 6
    static let emptyAspectRatio: CGFloat = 19 / 10
    static let borderWidth: CGFloat = 1
    static let addIconSize: CGFloat = 14
    static let retryButtonSize: CGFloat = 40
    static let retryIconSize: CGFloat = 18
    static let signatureSize = CGSize(width: 64, height: 48)
}

#Preview("Hero Empty", traits: .sizeThatFitsLayout) {
    HomeHeroCard(
        hero: .empty,
        topic: "하늘",
        onOpenPhotoUpload: {},
        onRetry: {}
    )
    .frame(width: 320)
    .padding()
    .chalkakTheme(.light)
}

#Preview("Hero Login Required", traits: .sizeThatFitsLayout) {
    HomeHeroCard(
        hero: .loginRequired,
        topic: "하늘",
        onOpenPhotoUpload: {},
        onRetry: {}
    )
    .frame(width: 320)
    .padding()
    .chalkakTheme(.light)
}

#Preview("Hero Photo", traits: .sizeThatFitsLayout) {
    HomeHeroCard(
        hero: .photo(HomePreviewData.heroPhoto),
        topic: "하늘",
        onOpenPhotoUpload: {},
        onRetry: {}
    )
    .frame(width: 320)
    .padding()
    .chalkakTheme(.light)
}
