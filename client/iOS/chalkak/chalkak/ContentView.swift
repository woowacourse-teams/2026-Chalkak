//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @State private var route: AppRoute = KeychainSessionStore.hasActiveSession() ? .home : .login

    var body: some View {
        Group {
            switch route {
            case .login:
                LoginView(
                    onAuthenticated: showHome,
                    onGuestAccessGranted: showHome
                )
            case .home:
                HomeView()
            }
        }
        .animation(.default, value: route)
    }

    private func showHome() {
        route = .home
    }
}

private enum AppRoute: Equatable {
    case login
    case home
}

#Preview {
    ContentView()
        .chalkakTheme(.light)
}
