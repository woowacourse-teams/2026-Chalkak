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

    @Test
    fun `주석과 문자열과 import의 패키지명은 코드 참조가 아니다`() {
        val source =
            "import com.stonefive.chalkak.data.Example\n" +
                "// com.stonefive.chalkak.data.Example\n" +
                "/* com.stonefive.chalkak.data.Example */\n" +
                "val quoted = \"com.stonefive.chalkak.data.Example\"\n" +
                "val raw = \"\"\"com.stonefive.chalkak.data.Example\"\"\"\n"

        assertTrue(!hasQualifiedReferenceInCode(source, "com.stonefive.chalkak.data."))
    }

    @Test
    fun `코드의 완전 수식 참조는 위반으로 감지한다`() {
        val source = "val example = com.stonefive.chalkak.data.Example"

        assertTrue(hasQualifiedReferenceInCode(source, "com.stonefive.chalkak.data."))
    }

    @Test
    fun `문자열 보간 안의 코드 참조는 위반으로 감지한다`() {
        val source = "val example = \"\${com.stonefive.chalkak.data.Example}\""

        assertTrue(hasQualifiedReferenceInCode(source, "com.stonefive.chalkak.data."))
    }

    private fun assertNoForbiddenPackageNamesInSource(
        packageName: String,
        forbiddenPrefix: String,
    ) {
        val matchingFiles = productionScope.files.filter { it.hasPackage(packageName) }
        assertTrue("No source files found in $packageName", matchingFiles.isNotEmpty())

        val violations = matchingFiles.filter { hasQualifiedReferenceInCode(it.text, forbiddenPrefix) }.map { it.path }
        assertTrue("$forbiddenPrefix appears in:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun hasQualifiedReferenceInCode(
        source: String,
        forbiddenPrefix: String,
    ): Boolean {
        val code = source.toCharArray()
        var index = 0
        var state = SourceState.CODE
        var blockDepth = 0
        val stringStates = ArrayDeque<SourceState>()
        val templateDepths = ArrayDeque<Int>()

        fun mask(count: Int = 1) {
            repeat(count) {
                if (code[index] != '\n' && code[index] != '\r') code[index] = ' '
                index++
            }
        }

        while (index < source.length) {
            when (state) {
                SourceState.CODE -> when {
                    source.startsWith("//", index) -> {
                        state = SourceState.LINE_COMMENT
                        mask(2)
                    }

                    source.startsWith("/*", index) -> {
                        state = SourceState.BLOCK_COMMENT
                        blockDepth = 1
                        mask(2)
                    }

                    source.startsWith("\"\"\"", index) -> {
                        state = SourceState.RAW_STRING
                        mask(3)
                    }

                    source[index] == '"' -> {
                        state = SourceState.STRING
                        mask()
                    }

                    source[index] == '\'' -> {
                        state = SourceState.CHAR
                        mask()
                    }

                    source[index] == '{' && templateDepths.isNotEmpty() -> {
                        templateDepths.addLast(templateDepths.removeLast() + 1)
                        index++
                    }

                    source[index] == '}' && templateDepths.isNotEmpty() -> {
                        val depth = templateDepths.removeLast()
                        if (depth == 1) {
                            state = stringStates.removeLast()
                        } else {
                            templateDepths.addLast(depth - 1)
                        }
                        index++
                    }

                    else -> index++
                }

                SourceState.LINE_COMMENT -> {
                    if (source[index] == '\n') state = SourceState.CODE
                    mask()
                }

                SourceState.BLOCK_COMMENT -> when {
                    source.startsWith("/*", index) -> {
                        blockDepth++
                        mask(2)
                    }

                    source.startsWith("*/", index) -> {
                        blockDepth--
                        if (blockDepth == 0) state = SourceState.CODE
                        mask(2)
                    }

                    else -> mask()
                }

                SourceState.STRING, SourceState.RAW_STRING -> when {
                    source.startsWith("\${", index) -> {
                        stringStates.addLast(state)
                        templateDepths.addLast(1)
                        state = SourceState.CODE
                        mask(2)
                    }

                    state == SourceState.RAW_STRING && source.startsWith("\"\"\"", index) -> {
                        state = SourceState.CODE
                        mask(3)
                    }

                    state == SourceState.STRING && source[index] == '\\' && index + 1 < source.length -> mask(2)

                    state == SourceState.STRING && source[index] == '"' -> {
                        state = SourceState.CODE
                        mask()
                    }

                    else -> mask()
                }

                SourceState.CHAR -> when {
                    source[index] == '\\' && index + 1 < source.length -> mask(2)

                    source[index] == '\'' -> {
                        state = SourceState.CODE
                        mask()
                    }

                    else -> mask()
                }
            }
        }

        val importDirective = Regex("(?m)^[ \\t]*import[ \\t]+[^\\r\\n]*")
        val codeWithoutImports = String(code).replace(importDirective, "")
        return Regex("(?<![\\w.])${Regex.escape(forbiddenPrefix)}").containsMatchIn(codeWithoutImports)
    }

    private enum class SourceState { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, RAW_STRING, CHAR }
}
