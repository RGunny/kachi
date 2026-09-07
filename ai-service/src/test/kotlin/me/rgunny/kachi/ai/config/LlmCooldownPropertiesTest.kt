package me.rgunny.kachi.ai.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("LlmCooldownProperties")
class LlmCooldownPropertiesTest {

    @Test
    @DisplayName("정상 범위 값으로 생성된다")
    fun createWithValidValues() {
        val properties = properties()

        assertEquals(Duration.ofSeconds(60), properties.default)
        assertEquals(Duration.ofMinutes(10), properties.max)
    }

    @Test
    @DisplayName("기본 cooldown은 양수여야 한다")
    fun rejectNonPositiveDefault() {
        assertFailsWith<IllegalArgumentException> { properties(default = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { properties(default = Duration.ofSeconds(-1)) }
    }

    @Test
    @DisplayName("최대 cooldown은 기본 cooldown 이상이어야 한다")
    fun rejectMaxSmallerThanDefault() {
        assertFailsWith<IllegalArgumentException> {
            properties(default = Duration.ofSeconds(60), max = Duration.ofSeconds(59))
        }

        val sameBoundary = properties(default = Duration.ofSeconds(60), max = Duration.ofSeconds(60))

        assertEquals(sameBoundary.default, sameBoundary.max)
    }

    private fun properties(
        default: Duration = Duration.ofSeconds(60),
        max: Duration = Duration.ofMinutes(10)
    ): LlmCooldownProperties {
        return LlmCooldownProperties(default = default, max = max)
    }
}
