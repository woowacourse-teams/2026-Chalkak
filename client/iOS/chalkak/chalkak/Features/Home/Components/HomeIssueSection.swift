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

            Button(action: onIssueClick) {
                VStack(alignment: .leading, spacing: theme.spacing.lg) {
                    theme.colors.surfaceElevated
                        .aspectRatio(Metrics.imageAspectRatio, contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .overlay {
                            GeometryReader { geometry in
                                ChalkakImage(
                                    source: .asset("issue_beta_thanks"),
                                    contentDescription: nil
                                )
                                .frame(width: geometry.size.width, height: geometry.size.height)
                                .clipped()
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: theme.shapes.large))

                    VStack(alignment: .leading, spacing: theme.spacing.sm) {
                        Text("함께 만들어주신 찰캌")
                            .font(theme.typography.subheadline)
                            .fontWeight(.bold)
                            .foregroundStyle(theme.colors.textPrimary)

                        Text("베타테스트에 함께해주셔서 감사합니다")
                            .font(theme.typography.caption)
                            .foregroundStyle(theme.colors.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("함께 만들어주신 찰캌")
        }
    }
}

private enum Metrics {
    static let imageAspectRatio: CGFloat = 1672 / 941
}

#Preview("Issue Section", traits: .sizeThatFitsLayout) {
    HomeIssueSection(onIssueClick: {})
        .padding()
        .chalkakTheme(.light)
}
