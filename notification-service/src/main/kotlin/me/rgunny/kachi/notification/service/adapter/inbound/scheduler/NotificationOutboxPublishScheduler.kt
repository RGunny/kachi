package me.rgunny.kachi.notification.service.adapter.inbound.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.dispatch.PublishNotificationDispatchUseCase
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetrics
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

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
    private val metrics: NotificationServiceMetrics,
) {

    /**
     * 한 번의 scheduler tick을 하나의 Timer 표본으로 기록하고,
     * 단건 발행 수량은 결과별 Counter로 누적한다.
     */
    @Scheduled(fixedDelayString = "\${kachi.notification.outbox.poll-interval}")
    fun publishPending() = runBlocking {
        val startedAt = System.nanoTime()
        val result = try {
            publishUseCase.publishPending()
        } catch (exception: CancellationException) {
            // 애플리케이션 종료 취소는 다음 tick에서 복구할 업무 실패가 아니다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordOutboxPublishTickFailure(elapsed(startedAt))
            log.error(
                "notification outbox publish tick failed. next scheduler tick will retry publishable outboxes",
                exception,
            )
            return@runBlocking
        }
        metrics.recordOutboxPublishTick(result, elapsed(startedAt))

        if (result.processed > 0) {
            log.info(
                "notification outbox publish tick processed={} published={} failed={} publishTickCompletedAt={}",
                result.processed,
                result.published,
                result.failed,
                result.publishTickCompletedAt,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationOutboxPublishScheduler::class.java)
    }

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }
}
