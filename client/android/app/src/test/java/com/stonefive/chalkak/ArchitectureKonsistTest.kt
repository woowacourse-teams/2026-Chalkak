package com.stonefive.chalkak

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureKonsistTest {
    private val productionScope by lazy {
        Konsist.scopeFromProject(moduleName = "app", sourceSetName = "main")
    }

    @Test
    fun `피처는 데이터 계층을 직접 참조하지 않는다`() {
        with(KoArchitectureCreator) {
            productionScope.assertArchitecture {
                val feature = Layer("Feature", "com.stonefive.chalkak.feature..")
                val data = Layer("Data", "com.stonefive.chalkak.data..")

                feature.doesNotDependOn(data)
            }
        }
        assertNoForbiddenPackageNamesInSource(
            packageName = "com.stonefive.chalkak.feature..",
            forbiddenPrefix = "com.stonefive.chalkak.data.",
        )
    }

    @Test
    fun `도메인은 바깥 계층에 의존하지 않는다`() {
        with(KoArchitectureCreator) {
            productionScope.assertArchitecture {
                val domain = Layer("Domain", "com.stonefive.chalkak.domain..")
                val feature = Layer("Feature", "com.stonefive.chalkak.feature..")
                val data = Layer("Data", "com.stonefive.chalkak.data..")
                val core = Layer("Core", "com.stonefive.chalkak.core..")

                domain.doesNotDependOn(feature, data, core)
            }
        }
        listOf(
            "com.stonefive.chalkak.feature.",
            "com.stonefive.chalkak.data.",
            "com.stonefive.chalkak.core.",
        ).forEach { forbiddenPrefix ->
            assertNoForbiddenPackageNamesInSource("com.stonefive.chalkak.domain..", forbiddenPrefix)
        }
    }

    @Test
    fun `디자인 시스템은 피처에 의존하지 않는다`() {
        with(KoArchitectureCreator) {
            productionScope.assertArchitecture {
                val designSystem = Layer("Design system", "com.stonefive.chalkak.core.designsystem..")
                val feature = Layer("Feature", "com.stonefive.chalkak.feature..")

                designSystem.doesNotDependOn(feature)
            }
        }
        assertNoForbiddenPackageNamesInSource(
            packageName = "com.stonefive.chalkak.core.designsystem..",
            forbiddenPrefix = "com.stonefive.chalkak.feature.",
        )
    }

    @Test
    fun `도메인은 안드로이드 API를 사용하지 않는다`() {
        val domainFiles = productionScope.files.filter {
            it.hasPackage("com.stonefive.chalkak.domain..")
        }
        assertTrue("No domain source files were found", domainFiles.isNotEmpty())

        val forbiddenImports = domainFiles.flatMap { file ->
            file.imports
                .filter { import ->
                    import.name.startsWith("android.") || import.name.startsWith("androidx.")
                }.map { import -> "${file.path}: ${import.name}" }
        }
        assertTrue("Domain imports Android APIs:\n${forbiddenImports.joinToString("\n")}", forbiddenImports.isEmpty())
        listOf("android.", "androidx.").forEach { forbiddenPrefix ->
            assertNoForbiddenPackageNamesInSource("com.stonefive.chalkak.domain..", forbiddenPrefix)
        }
    }

    private fun assertNoForbiddenPackageNamesInSource(
        packageName: String,
        forbiddenPrefix: String,
    ) {
        val matchingFiles = productionScope.files.filter { it.hasPackage(packageName) }
        assertTrue("No source files found in $packageName", matchingFiles.isNotEmpty())

        val violations = matchingFiles.filter { it.text.contains(forbiddenPrefix) }.map { it.path }
        assertTrue("$forbiddenPrefix appears in:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
