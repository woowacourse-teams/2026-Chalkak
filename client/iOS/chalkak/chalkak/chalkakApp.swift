//
//  chalkakApp.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI
import FirebaseCore
import GoogleSignIn
import KakaoSDKAuth
import KakaoSDKCommon
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        return true
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
        guard response.notification.request.identifier == UserNotificationDailyScheduler.requestIdentifier else {
            return
        }
        NotificationCenter.default.post(name: .dailyReminderNotificationTapped, object: nil)
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
