import FirebaseRemoteConfig
import Foundation

/// 이번 앱 실행 동안 고정되는 홈 ISSUE 배치 실험 값.
/// `isAssigned`는 원격 값을 받았는지를 뜻하며, 기본값만 쓰는 사용자는 실험 집계에서 뺀다.
struct HomeIssueExperiment: Equatable {
    var isIssueOnTop = false
    var isAssigned = false
}

/// Firebase Remote Config로 원격 설정을 받아 온다.
///
/// A/B 실험 값은 시작할 때 기기에 저장돼 있던 값으로 고정해 실행 중 화면이 바뀌지 않게 하고,
/// 새로 받은 값은 다음 실행부터 적용한다.
final class AppRemoteConfig {
    static let shared = AppRemoteConfig()

    private var launchExperiment: Task<HomeIssueExperiment, Never>?

    func start() {
        let remoteConfig = RemoteConfig.remoteConfig()
        let settings = RemoteConfigSettings()
#if DEBUG
        settings.minimumFetchInterval = 0
#else
        settings.minimumFetchInterval = Self.fetchIntervalSeconds
#endif
        remoteConfig.configSettings = settings

        let launchExperiment = Task {
            try? await remoteConfig.ensureInitialized()
            return Self.homeIssueExperiment(from: remoteConfig)
        }
        self.launchExperiment = launchExperiment
        Task {
            _ = await launchExperiment.value
            // 받지 못하면 마지막으로 적용한 값이나 기본값을 그대로 쓴다.
            _ = try? await remoteConfig.fetchAndActivate()
        }
    }

    func homeIssueExperiment() async -> HomeIssueExperiment {
        await launchExperiment?.value ?? HomeIssueExperiment()
    }

    private static func homeIssueExperiment(from remoteConfig: RemoteConfig) -> HomeIssueExperiment {
        guard let isIssueOnTop = remoteBool(forKey: homeIssueTopEnabledKey, in: remoteConfig) else {
            return HomeIssueExperiment()
        }
        return HomeIssueExperiment(isIssueOnTop: isIssueOnTop, isAssigned: true)
    }

    /// 원격 값이 없거나 true/false로 읽히지 않으면 nil을 돌려 기본값을 쓰게 한다.
    private static func remoteBool(forKey key: String, in remoteConfig: RemoteConfig) -> Bool? {
        let value = remoteConfig.configValue(forKey: key)
        guard value.source == .remote else { return nil }
        return Bool(value.stringValue.lowercased())
    }

    private static let homeIssueTopEnabledKey = "home_issue_top_enabled"
    private static let fetchIntervalSeconds: TimeInterval = 60 * 60
}
