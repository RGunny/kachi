package me.rgunny.kachi.ai.domain.watermark

import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("SummaryWatermark")
class SummaryWatermarkTest {
    private val now = AiTestFixture.NOW

    @Test
    @DisplayName("처리를 끝낸 지점으로 전진한다")
    fun advance() {
        val advanced = assertNotNull(
            watermark(now.minus(Duration.ofMinutes(10)))
                .advanceTo(position = now, updatedAt = now)
        )

        assertEquals(now, advanced.position)
        assertEquals(now, advanced.updatedAt)
    }

    @Test
    @DisplayName("현재 지점보다 이후가 아닌 시각으로는 옮기지 않는다")
    fun rejectWhenRequestIsNotAfterCurrent() {
        val current = watermark(now)

        assertNull(current.advanceTo(position = now.minus(Duration.ofMinutes(10)), updatedAt = now))
        assertNull(current.advanceTo(position = now, updatedAt = now))
    }

    private fun watermark(position: Instant): SummaryWatermark {
        return SummaryWatermark.initial(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            position = position,
            updatedAt = position
        )
    }
}
