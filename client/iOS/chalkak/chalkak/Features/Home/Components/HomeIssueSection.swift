import SwiftUI

struct HomeIssueSection: View {
    @Environment(\.chalkakTheme) private var theme
    var onIssueClick: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            Text("ISSUE")
                .font(theme.typography.headline)
                .fontWeight(.bold)
                .foregroundStyle(theme.colors.textPrimary)

            ForEach(HomeIssue.samples) { issue in
                Button(action: onIssueClick) {
                    VStack(alignment: .leading, spacing: theme.spacing.lg) {
                        theme.colors.surfaceElevated
                            .aspectRatio(Metrics.imageAspectRatio, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                            .overlay {
                                GeometryReader { geometry in
                                    ChalkakImage(
                                        source: issue.imageSource,
                                        contentDescription: nil
                                    )
                                    .frame(width: geometry.size.width, height: geometry.size.height)
                                    .clipped()
                                }
                            }
                            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))

                        VStack(alignment: .leading, spacing: theme.spacing.sm) {
                            Text(issue.title)
                                .font(theme.typography.subheadline)
                                .fontWeight(.bold)
                                .foregroundStyle(theme.colors.textPrimary)

                            Text(issue.description)
                                .font(theme.typography.caption)
                                .foregroundStyle(theme.colors.textSecondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(issue.title)

                if issue.id != HomeIssue.samples.last?.id {
                    Spacer(minLength: theme.spacing.xl - theme.spacing.lg)
                }
            }
        }
    }
}

private struct HomeIssue: Identifiable, Equatable {
    let id: String
    let title: String
    let description: String
    let imageSource: ChalkakImageSource

    static let samples = [
        HomeIssue(
            id: "composition",
            title: "바다에서 구도를 잘 잡는 방법",
            description: "수평선을 활용해 바다를 담는 작은 팁",
            imageSource: .asset("preview_sunset")
        ),
        HomeIssue(
            id: "popular",
            title: "지난 주 가장 인기있었던 사진",
            description: "좋아요를 많이 받은 전시 사진 모아보기",
            imageSource: .asset("preview_photo")
        )
    ]
}

private enum Metrics {
    static let imageAspectRatio: CGFloat = 2.35
}

#Preview("Issue Section", traits: .sizeThatFitsLayout) {
    HomeIssueSection(onIssueClick: {})
        .padding()
        .chalkakTheme(.light)
}
