package me.rgunny.kachi.ai.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("LlmFailoverProperties")
class LlmFailoverPropertiesTest {

    @Test
    @DisplayName("정상 범위 값으로 생성된다")
    fun createWithValidValues() {
        val properties = properties()

        assertEquals(Duration.ofSeconds(60), properties.defaultCooldown)
        assertEquals(Duration.ofMinutes(10), properties.maxCooldown)
    }

    @Test
    @DisplayName("기본 cooldown은 양수여야 한다")
    fun rejectNonPositiveDefaultCooldown() {
        assertFailsWith<IllegalArgumentException> { properties(defaultCooldown = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> {
            properties(defaultCooldown = Duration.ofSeconds(-1))
        }
    }

    @Test
    @DisplayName("최대 cooldown은 기본 cooldown 이상이어야 한다")
    fun rejectMaxCooldownSmallerThanDefault() {
        assertFailsWith<IllegalArgumentException> {
            properties(defaultCooldown = Duration.ofSeconds(60), maxCooldown = Duration.ofSeconds(59))
        }

        val sameBoundary = properties(
            defaultCooldown = Duration.ofSeconds(60),
            maxCooldown = Duration.ofSeconds(60)
        )

        assertEquals(sameBoundary.defaultCooldown, sameBoundary.maxCooldown)
    }

    private fun properties(
        defaultCooldown: Duration = Duration.ofSeconds(60),
        maxCooldown: Duration = Duration.ofMinutes(10)
    ): LlmFailoverProperties {
        return LlmFailoverProperties(defaultCooldown = defaultCooldown, maxCooldown = maxCooldown)
    }
}
