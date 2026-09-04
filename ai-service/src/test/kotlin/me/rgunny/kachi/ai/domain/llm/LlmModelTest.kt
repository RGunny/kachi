package me.rgunny.kachi.ai.domain.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("LlmModel")
class LlmModelTest {

    // Ollama tag는 이동 alias지만 digest 고정 호출이 미확인이라 예외다. 다른 제공자의 이동 alias는 여기서 막는다.
    @ParameterizedTest
    @EnumSource(value = LlmModel::class, mode = EnumSource.Mode.EXCLUDE, names = ["OLLAMA_QWEN3_27B"])
    @DisplayName("모델 code는 latest로 끝나는 이동 alias가 아니다")
    fun rejectMovingAlias(model: LlmModel) {
        assertFalse(model.code.endsWith("latest"), model.code)
    }

    @ParameterizedTest
    @EnumSource(LlmModel::class)
    @DisplayName("상수명은 제공자 상수명으로 시작한다")
    fun nameStartsWithProvider(model: LlmModel) {
        assertTrue(model.name.startsWith("${model.provider.name}_"), model.name)
    }

    @ParameterizedTest
    @EnumSource(LlmModel::class)
    @DisplayName("code는 비어 있지 않다")
    fun codeIsNotBlank(model: LlmModel) {
        assertTrue(model.code.isNotBlank(), model.name)
    }

    @Test
    @DisplayName("qualified code는 제공자 code와 모델 code를 슬래시로 잇는다")
    fun qualifiedCode() {
        assertEquals("groq/qwen/qwen3.8-27b", LlmModel.GROQ_QWEN3_27B.qualifiedCode)
    }
}
