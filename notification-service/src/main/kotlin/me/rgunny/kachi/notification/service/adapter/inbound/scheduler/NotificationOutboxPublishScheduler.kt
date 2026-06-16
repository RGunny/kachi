package me.rgunny.kachi.notification.service.adapter.inbound.scheduler

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.PublishNotificationDispatchUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * outbox PENDING/PUBLISHING recovery를 주기적으로 실행하는 scheduler adapter.
 *
 * 실제 발행/상태 전이 정책은 core의 PublishNotificationDispatchUseCase가 담당한다.
 * scheduler는 주기적 trigger와 운영 로그만 담당한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "kachi.notification.outbox.scheduler",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class NotificationOutboxPublishScheduler(
    private val publishUseCase: PublishNotificationDispatchUseCase,
) {

    @Scheduled(fixedDelayString = "\${kachi.notification.outbox.poll-interval}")
    fun publishPending() = runBlocking {
        val result = runCatching {
            publishUseCase.publishPending()
        }.onFailure { exception ->
            log.error(
                "notification outbox publish tick failed. next scheduler tick will retry publishable outboxes",
                exception,
            )
        }.getOrNull() ?: return@runBlocking

        if (result.processed > 0) {
            log.info(
                "notification outbox publish tick processed={} published={} failed={} handledAt={}",
                result.processed,
                result.published,
                result.failed,
                result.handledAt,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationOutboxPublishScheduler::class.java)
    }
}
