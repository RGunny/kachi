package me.rgunny.kachi.ai.fake

import kotlinx.coroutines.CompletableDeferred
import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.dto.keyword.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture
import java.time.Instant

/**
 * [release]가 완료될 때까지 실행 중 상태를 유지하는 키워드 확장 유스케이스 fake.
 *
 * 중복 실행 방지 lock은 "실행이 겹치는 순간"에만 관찰되므로, 실행을 붙잡아 둘 수단이 필요하다.
 */
class BlockingExpandKeywordsUseCase : ExpandKeywordsUseCase {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var executeCount = 0

    override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
        executeCount += 1
        started.complete(Unit)
        release.await()

        return ExpandKeywordsResult.from(
            AiRun.start(
                targetType = AiRunTargetType.KEYWORD_EXPANSION,
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
        val STARTED_AT: Instant = AiTestFixture.NOW
        val FINISHED_AT: Instant = AiTestFixture.NOW.plusSeconds(1)
    }
}
