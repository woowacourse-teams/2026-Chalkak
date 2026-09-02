//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(theme.colors.iconPrimary)
            Text("Hello, world!")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textPrimary)
        }
        .padding(theme.spacing.screenHorizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

#Preview {
    ContentView()
        .chalkakTheme(.light)
}
