package me.rgunny.kachi.ai.fake

import kotlinx.coroutines.CompletableDeferred
import me.rgunny.kachi.ai.application.port.dto.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.dto.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import java.time.Instant

/**
 * [release]가 완료될 때까지 실행 중 상태를 유지하는 뉴스 요약 유스케이스 fake.
 *
 * 중복 실행 방지 lock은 "실행이 겹치는 순간"에만 관찰되므로, 실행을 붙잡아 둘 수단이 필요하다.
 */
class BlockingSummarizeNewsUseCase : SummarizeNewsUseCase {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var executeCount = 0

    override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
        executeCount += 1
        started.complete(Unit)
        release.await()

        return SummarizeNewsResult.from(
            AiRun.start(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                requestedKeywords = command.keywords.size,
                startedAt = STARTED_AT
            ).complete(
                succeededCount = command.keywords.size,
                failureCount = 0,
                failureReason = null,
                provider = null,
                model = null,
                promptVersion = null,
                finishedAt = FINISHED_AT
            )
        )
    }

    companion object {
        val STARTED_AT: Instant = Instant.parse("2026-06-03T00:00:00Z")
        val FINISHED_AT: Instant = Instant.parse("2026-06-03T00:00:01Z")
    }
}
