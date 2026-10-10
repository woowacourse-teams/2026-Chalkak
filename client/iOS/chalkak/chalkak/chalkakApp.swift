//
//  chalkakApp.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI
import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import KakaoSDKAuth
import KakaoSDKCommon
import OSLog
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "Push"
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // FirebaseAppDelegateProxyEnabled가 꺼져 있어 APNs 토큰을 직접 넘겨야 FCM 토큰이 발급된다.
#if targetEnvironment(simulator)
        // 시뮬레이터의 APNs 토큰은 개발용인데 Firebase가 운영용으로 추정하므로 종류를 직접 알려준다.
        Messaging.messaging().setAPNSToken(deviceToken, type: .sandbox)
#else
        Messaging.messaging().apnsToken = deviceToken
#endif
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        logger.error("Remote notification registration failed: \(error.localizedDescription, privacy: .public)")
    }

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in
#if DEBUG
            // 실기기 수신 확인용. 디버그 빌드에서만 토큰을 남긴다.
            logger.debug("FCM token: \(fcmToken ?? "nil", privacy: .public)")
#endif
            PushDeviceRegistrar.shared.updateFCMToken(fcmToken)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let request = response.notification.request
        if request.identifier == UserNotificationDailyScheduler.requestIdentifier {
            NotificationCenter.default.post(name: .dailyReminderNotificationTapped, object: nil)
            return
        }

        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let tap = PushNotificationTap(userInfo: request.content.userInfo) else {
            return
        }
        PushTapRouter.shared.receive(tap)
    }
}

@main
struct chalkakApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        ChalkakFontRegistrar.registerFonts()

        let configuration = AppConfiguration()
        if let kakaoNativeAppKey = configuration.kakaoNativeAppKey {
            KakaoSDK.initSDK(appKey: kakaoNativeAppKey)
        }
        if let googleClientID = configuration.googleClientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(
                clientID: googleClientID,
                serverClientID: configuration.googleServerClientID
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .chalkakTheme(.light)
                .preferredColorScheme(.light)
                .onOpenURL { url in
                    if GIDSignIn.sharedInstance.handle(url) {
                        return
                    }

                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                    }
                }
        }
    }
}
