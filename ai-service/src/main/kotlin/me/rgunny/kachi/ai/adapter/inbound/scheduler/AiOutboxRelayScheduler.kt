package me.rgunny.kachi.ai.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayExecutor
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayFinished
import me.rgunny.kachi.ai.adapter.inbound.outbox.AiOutboxRelayLockUnavailable
import me.rgunny.kachi.ai.config.AiOutboxRelayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 outbox relay를 실행하는 scheduler.
 *
 * 발행 여부를 가리는 조건은 유스케이스가 저장된 상태에서 읽으므로 이 계층은 주기와 운영 로그만 맡는다.
 */
@Component
@ConditionalOnProperty(
    prefix = AiOutboxRelayProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class AiOutboxRelayScheduler(
    private val executor: AiOutboxRelayExecutor,
    private val properties: AiOutboxRelayProperties
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "AI outbox relay scheduler configured: publisherId={}, initialDelay={}, fixedDelay={}, batchSize={}, visibilityTimeout={}",
            properties.publisherId,
            properties.initialDelay,
            properties.fixedDelay,
            properties.batchSize,
            properties.publishingVisibilityTimeout
        )
    }

    @Scheduled(
        fixedDelayString = AiOutboxRelayProperties.FIXED_DELAY_EXPRESSION,
        initialDelayString = AiOutboxRelayProperties.INITIAL_DELAY_EXPRESSION
    )
    suspend fun relay() {
        runCatching {
            executor.execute()
        }.onSuccess { result ->
            // 1. 이미 실행 중인 tick이 있으면 실패로 보지 않고 이번 tick만 건너뛴다.
            when (result) {
                is AiOutboxRelayAlreadyRunning ->
                    log.info(
                        "Skip scheduled outbox relay because another relay is running: startedAt={}",
                        result.runningRelay.startedAt
                    )

                // 2. lock을 확인할 수 없는 것은 건너뛴 것이 아니라 장애이므로 원인과 함께 남긴다.
                is AiOutboxRelayLockUnavailable ->
                    log.warn(
                        "Skip scheduled outbox relay because the execution lock could not be checked",
                        result.cause
                    )

                // 3. 다룰 행이 없는 tick이 대부분이므로 처리한 행이 있을 때만 집계를 남긴다.
                is AiOutboxRelayFinished ->
                    if (result.result.processed > 0) {
                        log.info(
                            "Scheduled outbox relay finished: processed={}, published={}, retried={}, dead={}, staleRecovered={}, completedAt={}",
                            result.result.processed,
                            result.result.published,
                            result.result.retried,
                            result.result.dead,
                            result.result.staleRecovered,
                            result.result.completedAt
                        )
                    }
            }
        }.onFailure { error ->
            // 4. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
            log.warn("Scheduled outbox relay failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AiOutboxRelayScheduler::class.java)
    }
}
