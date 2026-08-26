package me.rgunny.kachi.ai.application.service.watermark

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fake.FakeSummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("FindSummaryWatermarksService")
class FindSummaryWatermarksServiceTest {
    private val watermarkPersistencePort = FakeSummaryWatermarkPersistencePort()
    private val service = FindSummaryWatermarksService(
        summaryWatermarkPersistencePort = watermarkPersistencePort,
        clock = AiTestFixture.CLOCK
    )

    @Test
    @DisplayName("진행 지점과 조회 시각의 차이를 초로 계산한다")
    fun calculateLagFromNow() = runBlocking {
        store(position = AiTestFixture.NOW.minus(LAG))

        val watermark = service.find().watermarks.single()

        assertEquals(AiRunTargetType.NEWS_SUMMARY, watermark.targetType)
        assertEquals(AiTestFixture.NOW.minus(LAG), watermark.position)
        assertEquals(LAG.seconds, watermark.lagSeconds)
    }

    @Test
    @DisplayName("저장된 적 없는 대상 종류는 결과에서 뺀다")
    fun excludeMissingWatermark() = runBlocking {
        assertTrue(service.find().watermarks.isEmpty())

        store(position = AiTestFixture.NOW, targetType = AiRunTargetType.NEWS_SUMMARY)

        assertEquals(
            listOf(AiRunTargetType.NEWS_SUMMARY),
            service.find().watermarks.map { it.targetType }
        )
    }

    @Test
    @DisplayName("진행 지점이 미래면 차이를 음수 그대로 돌려준다")
    fun keepNegativeLagForFuturePosition() = runBlocking {
        store(position = AiTestFixture.NOW.plus(LAG))

        assertEquals(-LAG.seconds, service.find().watermarks.single().lagSeconds)
    }

    private fun store(
        position: java.time.Instant,
        targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY
    ) {
        watermarkPersistencePort.watermarks[targetType] =
            AiTestFixture.watermark(position = position, targetType = targetType)
    }

    private companion object {
        val LAG: Duration = Duration.ofMinutes(30)
    }
}
