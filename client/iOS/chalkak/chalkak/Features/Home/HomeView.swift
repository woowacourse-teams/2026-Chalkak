import SwiftUI

struct HomeView: View {
    @Environment(\.chalkakTheme) private var theme
    @StateObject private var viewModel: HomeViewModel
    @State private var selectedItem: ChalkakBottomBarItem = .today

    init(configuration: AppConfiguration = AppConfiguration()) {
        _viewModel = StateObject(
            wrappedValue: HomeViewModel(
                repository: APIHomeRepository(baseURL: configuration.apiBaseURL)
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 0) {
                    homeTopBar
                    Divider()
                        .overlay(theme.colors.border)
                    homeContent
                }
            }

            ChalkakBottomBar(
                selectedItem: selectedItem,
                onSelect: { item in
                    selectedItem = item
                },
                onAdd: {}
            )
        }
        .background(theme.colors.background.ignoresSafeArea())
    }

    @ViewBuilder
    private var homeContent: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
                .tint(theme.colors.actionPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 140)
        case let .failure(message):
            VStack(spacing: theme.spacing.xl) {
                Text(message)
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.textSecondary)
                    .multilineTextAlignment(.center)

                Button("다시 시도", action: viewModel.load)
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.sm)
                    .background(theme.colors.actionPrimary)
                    .clipShape(Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.vertical, 100)
        case let .content(home):
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(topicDateText(home.topicDate))
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textPrimary)

                    Text(home.topic)
                        .font(theme.typography.title1)
                        .fontWeight(.bold)
                        .foregroundStyle(theme.colors.textPrimary)
                        .padding(.top, theme.spacing.sm)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .padding(.top, theme.spacing.lg)
                .padding(.bottom, theme.spacing.xl)

                if home.photos.isEmpty {
                    VStack(spacing: theme.spacing.md) {
                        Image(systemName: "photo.on.rectangle.angled")
                            .font(.system(size: 36))
                            .foregroundStyle(theme.colors.iconSecondary)

                        Text("아직 오늘의 사진이 없어요")
                            .font(theme.typography.body)
                            .foregroundStyle(theme.colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 80)
                } else {
                    LazyVStack(spacing: theme.spacing.xxl) {
                        ForEach(home.photos) { photo in
                            homePhotoCard(photo)
                        }
                    }
                    .padding(.bottom, theme.spacing.xxl)
                }
            }
        }
    }

    private func homePhotoCard(_ photo: HomePhoto) -> some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            ChalkakSignedImage(
                imageSource: .remote(photo.thumbnailImageURL ?? photo.originalImageURL),
                signatureSource: .remote(photo.signatureThumbnailImageURL ?? photo.signatureOriginalImageURL),
                contentDescription: photo.title ?? "오늘의 사진"
            )
            .frame(maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.photoCard))

            HStack(alignment: .top) {
                Text(photo.title?.isEmpty == false ? photo.title! : "무제")
                    .font(theme.typography.callout)
                    .foregroundStyle(theme.colors.textPrimary)

                Spacer()

                Label("\(photo.likeCount)", systemImage: photo.isLiked ? "heart.fill" : "heart")
                    .font(theme.typography.footnote)
                    .foregroundStyle(photo.isLiked ? theme.colors.error : theme.colors.textSecondary)
            }
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
    }

    private func topicDateText(_ value: String) -> String {
        let components = value.split(separator: "-")
        guard components.count == 3,
              let month = Int(components[1]),
              let day = Int(components[2]) else {
            return "오늘 · 오늘의 주제"
        }
        return "\(month)월 \(day)일 · 오늘의 주제"
    }

    private var homeTopBar: some View {
        HStack {
            ChalkakLogo()
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .padding(.top, theme.spacing.lg)
        .padding(.bottom, theme.spacing.sm)
    }
}

#Preview("Home route") {
    HomeView()
        .chalkakTheme(.light)
}
