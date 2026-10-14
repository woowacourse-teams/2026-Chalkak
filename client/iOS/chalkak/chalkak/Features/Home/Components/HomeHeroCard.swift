import SwiftUI

struct HomeHeroCard: View {
    @Environment(\.chalkakTheme) private var theme
    let hero: HomeHeroState
    let topic: String
    var onOpenPhotoUpload: () -> Void
    var onRetry: () -> Void

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
                EmptyHeroContent(topic: topic, onOpenPhotoUpload: onOpenPhotoUpload)
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
            }
        }
        .aspectRatio(Metrics.aspectRatio, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))
        .accessibilityIdentifier(accessibilityIdentifier)
    }
    private var accessibilityIdentifier: String {
        switch hero {
        case .loading: "home-loading"
        case .empty: "home-empty"
        default: "home-hero"
        }
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

private struct EmptyHeroContent: View {
    @Environment(\.chalkakTheme) private var theme
    let topic: String
    let onOpenPhotoUpload: () -> Void

    var body: some View {
        Button(action: onOpenPhotoUpload) {
            VStack(spacing: theme.spacing.xl) {
                Text("\"\(displayTopic)\" 사진을 공유하지 않았어요")
                    .font(theme.typography.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(theme.colors.textPrimary)
                    .multilineTextAlignment(.center)

                Image(systemName: "plus")
                    .font(.system(size: Metrics.addIconSize, weight: .semibold))
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .frame(width: Metrics.addButtonSize, height: Metrics.addButtonSize)
                    .background(theme.colors.actionPrimary, in: Circle())
                    .accessibilityHidden(true)
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
        .accessibilityLabel("\(displayTopic) 사진을 공유하지 않았어요")
    }

    private var displayTopic: String {
        topic.isEmpty ? "오늘의 주제" : topic
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
    static let borderWidth: CGFloat = 1
    static let addButtonSize: CGFloat = 36
    static let addIconSize: CGFloat = 22
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
