package me.rgunny.kachi.ai.adapter.outbound.llm

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("LlmCooldownSettings")
class LlmCooldownSettingsTest {

    @Test
    @DisplayName("정상 범위 값으로 생성된다")
    fun createWithValidValues() {
        val settings = settings()

        assertEquals(Duration.ofSeconds(60), settings.default)
        assertEquals(Duration.ofMinutes(10), settings.max)
    }

    @Test
    @DisplayName("기본 cooldown은 양수여야 한다")
    fun rejectNonPositiveDefault() {
        assertFailsWith<IllegalArgumentException> { settings(default = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { settings(default = Duration.ofSeconds(-1)) }
    }

    @Test
    @DisplayName("최대 cooldown은 기본 cooldown 이상이어야 한다")
    fun rejectMaxSmallerThanDefault() {
        assertFailsWith<IllegalArgumentException> {
            settings(default = Duration.ofSeconds(60), max = Duration.ofSeconds(59))
        }

        val sameBoundary = settings(default = Duration.ofSeconds(60), max = Duration.ofSeconds(60))

        assertEquals(sameBoundary.default, sameBoundary.max)
    }

    private fun settings(
        default: Duration = Duration.ofSeconds(60),
        max: Duration = Duration.ofMinutes(10)
    ): LlmCooldownSettings {
        return LlmCooldownSettings(default = default, max = max)
    }
}
