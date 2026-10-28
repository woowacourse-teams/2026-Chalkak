import SwiftUI

struct HomeIssueSection: View {
    @Environment(\.chalkakTheme) private var theme
    let banner: Banner?
    var onIssueClick: (URL) -> Void
    var showHeader = true

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            if showHeader {
                Text("ISSUE")
                    .font(theme.typography.headline)
                    .fontWeight(.bold)
                    .foregroundStyle(theme.colors.textPrimary)
            }

            if let banner {
                Button(action: { onIssueClick(banner.webViewURL) }) {
                    VStack(alignment: .leading, spacing: theme.spacing.lg) {
                        theme.colors.surfaceElevated
                            .aspectRatio(Metrics.imageAspectRatio, contentMode: .fit)
                            .frame(maxWidth: .infinity)
                            .overlay {
                                GeometryReader { geometry in
                                    ChalkakImage(
                                        source: .remote(banner.thumbnailURL),
                                        contentDescription: nil,
                                        contentMode: .fill
                                    )
                                    .frame(width: geometry.size.width, height: geometry.size.height)
                                    .clipped()
                                }
                            }
                            .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))

                        VStack(alignment: .leading, spacing: theme.spacing.sm) {
                            Text(banner.title)
                                .font(theme.typography.subheadline)
                                .fontWeight(.bold)
                                .foregroundStyle(theme.colors.textPrimary)

                            Text(banner.subtitle)
                                .font(theme.typography.caption)
                                .foregroundStyle(theme.colors.textSecondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(banner.title)
            } else {
                theme.colors.surfaceElevated
                    .aspectRatio(Metrics.imageAspectRatio, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))
                    .accessibilityLabel("배너 불러오는 중")
            }
        }
    }
}

private enum Metrics {
    static let imageAspectRatio: CGFloat = 1672 / 941
}

#Preview("Issue Section", traits: .sizeThatFitsLayout) {
    HomeIssueSection(banner: HomePreviewData.banner, onIssueClick: { _ in })
        .padding()
        .chalkakTheme(.light)
}
