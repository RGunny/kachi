package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizedNewsResult
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiSkipReason
import me.rgunny.kachi.ai.domain.summary.NewsSummaryId
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("NewsSummaryOutcome")
class NewsSummaryOutcomeTest {

    @Test
    @DisplayName("성공, 실패, skip을 각각 세어 실행 기록 집계로 접는다")
    fun countEachOutcome() {
        val outcome = NewsSummaryOutcome.of(
            listOf(
                succeeded("NVIDIA"),
                succeeded("TESLA"),
                failed(AiFailureReason.TIMEOUT),
                skipped(AiSkipReason.NO_INPUT)
            )
        )

        assertEquals(2, outcome.succeededCount)
        assertEquals(1, outcome.failureCount)
        assertEquals(1, outcome.skippedCount)
        assertEquals(2, outcome.summaries.size)
    }

    @Test
    @DisplayName("실패 원인은 가장 먼저 발생한 것을 대표로 남긴다")
    fun keepFirstFailureReason() {
        val outcome = NewsSummaryOutcome.of(
            listOf(
                failed(AiFailureReason.RATE_LIMITED),
                failed(AiFailureReason.INVALID_RESPONSE)
            )
        )

        assertEquals(AiFailureReason.RATE_LIMITED, outcome.failureReason)
    }

    @Test
    @DisplayName("skip 사유는 순서와 무관하게 PROVIDER_UNAVAILABLE을 우선한다")
    fun preferProviderUnavailableSkipReason() {
        val outcome = NewsSummaryOutcome.of(
            listOf(
                skipped(AiSkipReason.NO_INPUT),
                skipped(AiSkipReason.PROVIDER_UNAVAILABLE),
                skipped(AiSkipReason.NO_INPUT)
            )
        )

        // NO_INPUT은 조치할 것이 없지만 PROVIDER_UNAVAILABLE은 장애 신호다. 뒤에 나와도 이쪽이 남아야 한다.
        assertEquals(AiSkipReason.PROVIDER_UNAVAILABLE, outcome.skipReason)
        assertEquals(3, outcome.skippedCount)
    }

    @Test
    @DisplayName("PROVIDER_UNAVAILABLE이 없으면 최초 skip 사유를 남긴다")
    fun keepFirstSkipReasonWithoutProviderFailure() {
        val outcome = NewsSummaryOutcome.of(listOf(skipped(AiSkipReason.NO_INPUT)))

        assertEquals(AiSkipReason.NO_INPUT, outcome.skipReason)
    }

    @Test
    @DisplayName("생성 metadata는 최초 성공분을 대표로 남긴다")
    fun keepFirstSuccessMetadata() {
        val outcome = NewsSummaryOutcome.of(listOf(succeeded("NVIDIA"), succeeded("TESLA")))

        assertEquals(AiTestFixture.PROVIDER, outcome.metadata?.provider)
        assertEquals(AiTestFixture.MODEL, outcome.metadata?.model)
    }

    @Test
    @DisplayName("결과가 없으면 대표 값도 남기지 않는다")
    fun keepNothingWhenOutcomesAreEmpty() {
        val outcome = NewsSummaryOutcome.of(emptyList())

        assertEquals(0, outcome.succeededCount)
        assertEquals(0, outcome.failureCount)
        assertEquals(0, outcome.skippedCount)
        assertNull(outcome.failureReason)
        assertNull(outcome.skipReason)
        assertNull(outcome.metadata)
    }

    private fun succeeded(keyword: String): KeywordOutcome.Succeeded {
        return KeywordOutcome.Succeeded(
            summary = SummarizedNewsResult(
                id = NewsSummaryId.newId(),
                keyword = AiKeyword.of(keyword),
                title = "$keyword 요약",
                sentiment = NewsSummarySentiment.NEUTRAL,
                reused = false
            ),
            metadata = AiTestFixture.newsSummaryMetadata()
        )
    }

    private fun failed(reason: AiFailureReason): KeywordOutcome.Failed {
        return KeywordOutcome.Failed(reason = reason, abortsRun = false)
    }

    private fun skipped(reason: AiSkipReason): KeywordOutcome.Skipped {
        return KeywordOutcome.Skipped(reason)
    }
}
