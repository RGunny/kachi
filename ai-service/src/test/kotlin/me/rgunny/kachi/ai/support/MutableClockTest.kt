package me.rgunny.kachi.ai.support

import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.assertEquals

@DisplayName("MutableClock")
class MutableClockTest {

    @Test
    @DisplayName("시작 시각은 고정 시각과 같다")
    fun startsAtFixedInstant() {
        assertEquals(AiTestFixture.NOW, MutableClock().instant())
    }

    @Test
    @DisplayName("진행시킨 만큼 시각이 움직이고 누적된다")
    fun advanceAccumulates() {
        val clock = MutableClock()

        clock.advance(Duration.ofMinutes(1))
        assertEquals(AiTestFixture.NOW.plus(Duration.ofMinutes(1)), clock.instant())

        clock.advance(Duration.ofSeconds(30))
        assertEquals(AiTestFixture.NOW.plus(Duration.ofSeconds(90)), clock.instant())
    }

    @Test
    @DisplayName("zone은 UTC다")
    fun zoneIsUtc() {
        assertEquals(ZoneOffset.UTC, MutableClock().zone)
    }
}
