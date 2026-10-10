import FirebaseCore
import FirebaseMessaging
import Foundation
import OSLog

/// 로그인과 FCM 토큰이 모두 준비됐을 때 현재 기기를 서버에 등록한다.
/// 로그인 직후, FCM 토큰 변경, 앱 재시작마다 같은 API로 갱신한다.
final class PushDeviceRegistrar {
    typealias AuthenticationProvider = @MainActor () -> Bool
    typealias FCMTokenProvider = @MainActor () async -> String?
    typealias Registration = @MainActor (String) async throws -> Void

    static let shared = PushDeviceRegistrar()

    private let isAuthenticated: AuthenticationProvider
    private let currentFCMToken: FCMTokenProvider
    private let register: Registration
    private var latestFCMToken: String?
    private var registrationTask: Task<Void, Never>?
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "Push"
    )

    init(
        isAuthenticated: @escaping AuthenticationProvider = {
            KeychainSessionStore.hasAuthenticatedSession()
        },
        currentFCMToken: @escaping FCMTokenProvider = PushDeviceRegistrar.firebaseToken,
        register: @escaping Registration = { fcmToken in
            try await PushDeviceAPIClient(baseURL: AppConfiguration().apiBaseURL)
                .register(fcmToken: fcmToken)
        }
    ) {
        self.isAuthenticated = isAuthenticated
        self.currentFCMToken = currentFCMToken
        self.register = register
    }

    func updateFCMToken(_ fcmToken: String?) {
        latestFCMToken = fcmToken
        registerCurrentDevice()
    }

    /// 앞선 등록이 끝난 뒤 실행해 마지막 토큰과 로그인 상태가 서버에 남게 한다.
    @discardableResult
    func registerCurrentDevice() -> Task<Void, Never> {
        let previousTask = registrationTask
        let task = Task {
            await previousTask?.value
            await registerIfReady()
        }
        registrationTask = task
        return task
    }

    private func registerIfReady() async {
        guard isAuthenticated() else { return }

        let knownToken = latestFCMToken
        let resolvedToken: String?
        if let knownToken {
            resolvedToken = knownToken
        } else {
            resolvedToken = await currentFCMToken()
        }
        guard let fcmToken = resolvedToken, !fcmToken.isEmpty else {
            logger.debug("Push device registration skipped because FCM token is not ready")
            return
        }

        do {
            try await register(fcmToken)
            logger.debug("Push device registered")
        } catch is CancellationError {
            return
        } catch {
            // 등록 실패는 로그인 상태에 영향을 주지 않는다. 다음 토큰 갱신·앱 시작 때 다시 시도한다.
            logger.error("Push device registration failed: \(String(describing: error), privacy: .public)")
        }
    }

    private static func firebaseToken() async -> String? {
        guard FirebaseApp.app() != nil else { return nil }
        return try? await Messaging.messaging().token()
    }
}
