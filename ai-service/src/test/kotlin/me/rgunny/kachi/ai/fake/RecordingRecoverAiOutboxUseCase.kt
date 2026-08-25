package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.exception.AiException
import me.rgunny.kachi.ai.application.port.inbound.outbox.RecoverAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.AiOutboxSummary
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxCommand
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxResult
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 전달받은 command를 그대로 보관하는 outbox 복구 유스케이스 fake.
 *
 * [failure]를 걸면 그 실패를 던진다.
 * 오류 응답 규약은 컨트롤러가 아니라 advice가 만들므로 실패 경로도 유스케이스 쪽에서 만들어 준다.
 */
class RecordingRecoverAiOutboxUseCase : RecoverAiOutboxUseCase {
    var invokeCount = 0
    var lastCommand: RecoverAiOutboxCommand? = null
    var failure: AiException? = null

    override suspend fun recover(command: RecoverAiOutboxCommand): RecoverAiOutboxResult {
        invokeCount += 1
        lastCommand = command
        failure?.let { throw it }

        val recovered = AiTestFixture.restoredOutbox(
            id = command.outboxId,
            status = AiOutboxStatus.DEAD,
            retryCount = 5,
            lastError = "broker down"
        ).recoverToPending(AiTestFixture.NOW)

        return RecoverAiOutboxResult(
            outbox = AiOutboxSummary.from(recovered),
            recoveredAt = AiTestFixture.NOW
        )
    }
}
