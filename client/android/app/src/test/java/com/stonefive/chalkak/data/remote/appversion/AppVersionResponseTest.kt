package com.stonefive.chalkak.data.remote.appversion

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppVersionResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `생략된 선택 필드와 명시적인 null은 미설정 응답으로 읽는다`() {
        val responses = listOf(
            """{"platform":"ANDROID"}""",
            """{"platform":"ANDROID","minimumSupportedVersion":null}""",
            """{"platform":"ANDROID","storeUrl":null}""",
            """{"platform":"ANDROID","minimumSupportedVersion":null,"storeUrl":null}""",
        )

        responses.forEach { response ->
            assertEquals(
                AppVersionResponse("ANDROID", null, null),
                json.decodeFromString<AppVersionResponse>(response),
            )
        }
    }

    @Test
    fun `선택 필드 하나가 생략되어도 나머지 값은 유지한다`() {
        assertEquals(
            AppVersionResponse("ANDROID", minimumSupportedVersion = "1.1.2"),
            json.decodeFromString<AppVersionResponse>(
                """{"platform":"ANDROID","minimumSupportedVersion":"1.1.2"}""",
            ),
        )
        assertEquals(
            AppVersionResponse("ANDROID", storeUrl = "https://play.google.com/test"),
            json.decodeFromString<AppVersionResponse>(
                """{"platform":"ANDROID","storeUrl":"https://play.google.com/test"}""",
            ),
        )
    }

    @Test
    fun `platform은 필수 필드로 유지한다`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<AppVersionResponse>("{}")
        }
    }
}
