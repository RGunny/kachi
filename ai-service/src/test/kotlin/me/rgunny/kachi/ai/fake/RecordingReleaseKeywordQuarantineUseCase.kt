package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.exception.AiException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseKeywordQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineCommand
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineResult
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 전달받은 command를 그대로 보관하는 격리 해제 유스케이스 fake.
 *
 * [failure]를 걸면 그 실패를 던진다.
 * 오류 응답 규약은 컨트롤러가 아니라 advice가 만들므로 실패 경로도 유스케이스 쪽에서 만들어 준다.
 */
class RecordingReleaseKeywordQuarantineUseCase : ReleaseKeywordQuarantineUseCase {
    var invokeCount = 0
    var lastCommand: ReleaseKeywordQuarantineCommand? = null
    var failure: AiException? = null

    override suspend fun release(command: ReleaseKeywordQuarantineCommand): ReleaseKeywordQuarantineResult {
        invokeCount += 1
        lastCommand = command
        failure?.let { throw it }

        val released = AiTestFixture.quarantine(
            keyword = command.keyword,
            consecutiveFailures = AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD,
            targetType = command.targetType
        ).release(AiTestFixture.NOW)

        return ReleaseKeywordQuarantineResult(
            quarantine = KeywordQuarantineSummary.from(released),
            releasedAt = AiTestFixture.NOW
        )
    }
}
