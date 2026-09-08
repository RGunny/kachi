package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.exception.CollectorException
import me.rgunny.kachi.collector.application.port.inbound.outbox.RecoverCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxCommand
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxResult
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import me.rgunny.kachi.collector.fixture.CollectorTestFixture

/**
 * 전달받은 command를 그대로 보관하는 outbox 복구 유스케이스 fake.
 *
 * [failure]를 걸면 그 실패를 던진다.
 * 오류 응답 규약은 컨트롤러가 아니라 advice가 만들므로 실패 경로도 유스케이스 쪽에서 만들어 준다.
 */
class RecordingRecoverCollectorOutboxUseCase : RecoverCollectorOutboxUseCase {
    var invokeCount = 0
    var lastCommand: RecoverCollectorOutboxCommand? = null
    var failure: CollectorException? = null

    override suspend fun recover(command: RecoverCollectorOutboxCommand): RecoverCollectorOutboxResult {
        invokeCount += 1
        lastCommand = command
        failure?.let { throw it }

        val recovered = CollectorTestFixture.restoredOutbox(
            id = command.outboxId,
            status = CollectorOutboxStatus.DEAD,
            retryCount = 5,
            lastError = "broker down"
        ).recoverToPending(CollectorTestFixture.NOW)

        return RecoverCollectorOutboxResult(
            outbox = CollectorOutboxSummary.from(recovered),
            recoveredAt = CollectorTestFixture.NOW
        )
    }
}
