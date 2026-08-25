package me.rgunny.kachi.ai.application.service.outbox

import me.rgunny.kachi.ai.application.exception.AiOutboxNotFoundException
import me.rgunny.kachi.ai.application.exception.AiOutboxNotRecoverableException
import me.rgunny.kachi.ai.application.port.inbound.outbox.RecoverAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.AiOutboxSummary
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxCommand
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxResult
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * DEAD로 남은 행을 다시 발행 대상으로 되돌린다.
 *
 * 상태 전이만 하고 발행은 하지 않는다. 되돌린 행은 relay가 다음 tick에 가져간다.
 * 저장은 조건부 쓰기라, 조회와 저장 사이에 다른 복구가 끝났으면 이 요청은 실패로 끝난다.
 */
@Service
class RecoverAiOutboxService(
    private val outboxPersistencePort: AiOutboxPersistencePort,
    private val clock: Clock
) : RecoverAiOutboxUseCase {

    override suspend fun recover(command: RecoverAiOutboxCommand): RecoverAiOutboxResult {
        val outbox = outboxPersistencePort.findById(command.outboxId)
            ?: throw AiOutboxNotFoundException(command.outboxId)

        // 1. 발행 대기 중이거나 이미 발행된 행은 복구 대상이 아니다.
        if (outbox.status != AiOutboxStatus.DEAD) {
            throw AiOutboxNotRecoverableException(command.outboxId, outbox.status)
        }

        val now = Instant.now(clock)
        logRecovery(outbox)
        val recovered = outbox.recoverToPending(now)

        // 2. DEAD일 때만 저장된다. 밀렸다면 이미 다른 복구가 목적을 달성했지만 이 요청이 한 일은 없다.
        if (!outboxPersistencePort.recoverDead(recovered)) {
            log.warn("Outbox recovery lost the race with another recovery: id={}", outbox.id.value)

            throw AiOutboxNotRecoverableException(command.outboxId, outbox.status)
        }

        return RecoverAiOutboxResult(
            outbox = AiOutboxSummary.from(recovered),
            recoveredAt = now
        )
    }

    /**
     * 수동 개입은 이후 발행 결과를 해석하는 기준이 되므로 복구 직전 상태를 남긴다.
     */
    private fun logRecovery(outbox: AiOutbox) {
        log.info(
            "Recovering dead outbox: id={}, eventType={}, retryCount={}, lastError={}",
            outbox.id.value,
            outbox.eventType,
            outbox.retryCount,
            outbox.lastError
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecoverAiOutboxService::class.java)
    }
}
