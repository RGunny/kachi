package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("LlmProvider")
class LlmProviderTest {

    @ParameterizedTest
    @EnumSource(LlmProvider::class)
    @DisplayName("code로 다시 찾을 수 있다")
    fun roundTripByCode(provider: LlmProvider) {
        assertEquals(provider, LlmProvider.ofCode(provider.code))
    }

    @ParameterizedTest
    @EnumSource(LlmProvider::class)
    @DisplayName("code는 소문자 상수명이다")
    fun codeIsLowercaseName(provider: LlmProvider) {
        assertEquals(provider.name.lowercase(), provider.code)
    }

    @Test
    @DisplayName("code는 제공자마다 다르다")
    fun codesAreUnique() {
        assertEquals(LlmProvider.entries.size, LlmProvider.entries.map { it.code }.toSet().size)
    }

    @Test
    @DisplayName("모르는 code는 실패한다")
    fun rejectUnknownCode() {
        val error = assertFailsWith<IllegalArgumentException> { LlmProvider.ofCode("gemini") }

        assertTrue(error.message!!.contains("gemini"))
    }
}
