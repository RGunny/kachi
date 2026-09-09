package me.rgunny.kachi.collector.application.service.outbox

import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotFoundException
import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotRecoverableException
import me.rgunny.kachi.collector.application.port.inbound.outbox.RecoverCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxCommand
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxResult
import me.rgunny.kachi.collector.application.port.outbound.persistence.CollectorOutboxPersistencePort
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * DEAD로 남은 행을 다시 발행 대상으로 되돌리는 유스케이스.
 *
 * 상태 전이만 하고 발행은 하지 않는다. 되돌린 행은 relay가 다음 tick에 가져간다.
 * 저장은 조건부 쓰기라, 조회와 저장 사이에 다른 복구가 끝났으면 이 요청은 실패로 끝난다.
 */
@Service
class RecoverCollectorOutboxService(
    private val outboxPersistencePort: CollectorOutboxPersistencePort,
    private val clock: Clock
) : RecoverCollectorOutboxUseCase {

    override suspend fun recover(command: RecoverCollectorOutboxCommand): RecoverCollectorOutboxResult {
        val outbox = outboxPersistencePort.findById(command.outboxId)
            ?: throw CollectorOutboxNotFoundException(command.outboxId)

        // 1. 발행 대기 중이거나 이미 발행된 행은 복구 대상이 아니다.
        if (outbox.status != CollectorOutboxStatus.DEAD) {
            throw CollectorOutboxNotRecoverableException(command.outboxId, outbox.status)
        }

        val now = Instant.now(clock)
        logRecovery(outbox)
        val recovered = outbox.recoverToPending(now)

        // 2. DEAD일 때만 저장된다. 밀렸다면 이미 다른 복구가 목적을 달성했지만 이 요청이 한 일은 없다.
        if (!outboxPersistencePort.recoverDead(recovered)) {
            log.warn("Outbox recovery lost the race with another recovery: id={}", outbox.id.value)

            throw CollectorOutboxNotRecoverableException(command.outboxId, outbox.status)
        }

        return RecoverCollectorOutboxResult(
            outbox = CollectorOutboxSummary.from(recovered),
            recoveredAt = now
        )
    }

    /**
     * 수동 개입은 이후 발행 결과를 해석하는 기준이 되므로 복구 직전 상태를 남긴다.
     */
    private fun logRecovery(outbox: CollectorOutbox) {
        log.info(
            "Recovering dead outbox: id={}, eventType={}, retryCount={}, lastError={}",
            outbox.id.value,
            outbox.eventType,
            outbox.retryCount,
            outbox.lastError
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecoverCollectorOutboxService::class.java)
    }
}
