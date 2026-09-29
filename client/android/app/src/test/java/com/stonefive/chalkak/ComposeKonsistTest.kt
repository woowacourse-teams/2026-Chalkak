package com.stonefive.chalkak

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeKonsistTest {
    @Test
    fun `화면은 뷰모델과 내비게이션 객체를 직접 사용하지 않는다`() {
        val scope = Konsist.scopeFromProject(moduleName = "app", sourceSetName = "main")
        val screens = scope
            .files
            .filter { it.hasPackage("com.stonefive.chalkak.feature..") }
            .flatMap { it.functions(includeNested = false, includeLocal = false) }
            .filter { it.name.endsWith("Screen") && it.hasAnnotationWithName("Composable") }

        assertTrue("검사할 Screen 함수가 없습니다", screens.isNotEmpty())

        val forbiddenType = Regex("\\b(?:\\w*ViewModel|\\w*NavController)\\b")
        val forbiddenLookup = Regex("\\b(?:hiltViewModel|viewModel)\\s*\\(")
        val forbiddenNavigation = Regex("\\bnavController\\s*\\.")
        val violations = screens
            .filter { screen ->
                screen.parameters.any { it.text.contains(forbiddenType) } ||
                    screen.text.contains(forbiddenType) ||
                    screen.text.contains(forbiddenLookup) ||
                    screen.text.contains(forbiddenNavigation)
            }.map { "${it.path}: ${it.name}" }

        assertTrue("Screen의 ViewModel/NavController 직접 사용:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
