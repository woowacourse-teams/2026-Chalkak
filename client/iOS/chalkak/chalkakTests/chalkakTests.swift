//
//  chalkakTests.swift
//  chalkakTests
//
//  Created by 정찬 on 8/9/26.
//

import Testing
import CoreGraphics
@testable import chalkak

struct chalkakTests {
    @Test("Android와 동일한 spacing 토큰을 제공한다")
    func providesSharedSpacingTokens() {
        let spacing = ChalkakTheme.light.spacing

        #expect(spacing.none == 0)
        #expect(spacing.xs == 4)
        #expect(spacing.sm == 8)
        #expect(spacing.md == 12)
        #expect(spacing.lg == 16)
        #expect(spacing.xl == 24)
        #expect(spacing.xxl == 40)
        #expect(spacing.screenHorizontal == 25)
    }

    @Test("Android와 동일한 shape 토큰을 제공한다")
    func providesSharedShapeTokens() {
        let shapes = ChalkakTheme.light.shapes

        #expect(shapes.small == 7)
        #expect(shapes.button == 12)
        #expect(shapes.input == 14)
        #expect(shapes.photoCard == 12)
        #expect(shapes.large == 16)
        #expect(shapes.xlarge == 18)
        #expect(shapes.sheet == 28)
    }

    @Test("테마가 색상·타이포그래피·spacing·shape를 함께 제공한다")
    func providesCompleteLightTheme() {
        let theme = ChalkakTheme.light

        _ = theme.colors
        _ = theme.typography
        _ = theme.spacing
        _ = theme.shapes

        #expect(theme.spacing.screenHorizontal == 25)
    }
}
