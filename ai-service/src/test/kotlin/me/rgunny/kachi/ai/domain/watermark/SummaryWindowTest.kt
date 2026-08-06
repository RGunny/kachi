package me.rgunny.kachi.ai.domain.watermark

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("SummaryWindow")
class SummaryWindowTest {
    private val now = Instant.parse("2026-06-03T00:00:00Z")
    private val overlap = Duration.ofMinutes(5)
    private val maxLookback = Duration.ofHours(6)

    @Test
    @DisplayName("watermark에서 overlap만큼 물러난 지점부터 현재까지를 구간으로 잡는다")
    fun startFromWatermarkMinusOverlap() {
        val watermark = now.minus(Duration.ofMinutes(10))

        val window = resolve(watermark)

        assertEquals(watermark.minus(overlap), window.from)
        assertEquals(now, window.to)
        assertFalse(window.truncated)
        assertNull(window.skippedFrom)
    }

    @Test
    @DisplayName("watermark가 없는 최초 기동에서는 현재 시각에서 overlap만큼만 되돌아본다")
    fun startFromNowMinusOverlapWhenWatermarkIsAbsent() {
        val window = resolve(watermark = null)

        assertEquals(now.minus(overlap), window.from)
        assertEquals(now, window.to)
        assertFalse(window.truncated)
    }

    @Test
    @DisplayName("장기 정지로 watermark가 뒤처져 있어도 멈춰 있던 구간을 이어서 처리한다")
    fun coverStalledRange() {
        val watermark = now.minus(Duration.ofHours(3))

        val window = resolve(watermark)

        assertEquals(watermark.minus(overlap), window.from)
        assertFalse(window.truncated)
    }

    @Test
    @DisplayName("maxLookback보다 오래 정지했으면 하한까지만 처리하고 건너뛴 구간을 남긴다")
    fun truncateByMaxLookback() {
        val watermark = now.minus(Duration.ofHours(10))

        val window = resolve(watermark)

        assertEquals(now.minus(maxLookback), window.from)
        assertTrue(window.truncated)
        assertEquals(watermark.minus(overlap), window.skippedFrom)
    }

    @Test
    @DisplayName("watermark가 현재보다 앞서 있어도 빈 구간을 만들지 않는다")
    fun clampFutureWatermark() {
        val window = resolve(watermark = now.plus(Duration.ofHours(1)))

        assertEquals(now, window.from)
        assertEquals(now, window.to)
    }

    @Test
    @DisplayName("maxLookback이 0 이하이거나 overlap이 음수이면 구간을 계산할 수 없다")
    fun rejectInvalidPolicy() {
        assertFailsWith<IllegalArgumentException> {
            SummaryWindow.resolve(watermark = null, now = now, overlap = overlap, maxLookback = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            SummaryWindow.resolve(
                watermark = null,
                now = now,
                overlap = Duration.ofMinutes(-1),
                maxLookback = maxLookback
            )
        }
    }

    private fun resolve(watermark: Instant?): SummaryWindow {
        return SummaryWindow.resolve(
            watermark = watermark,
            now = now,
            overlap = overlap,
            maxLookback = maxLookback
        )
    }
}
