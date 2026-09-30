package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.sender.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.worker.adapter.monitoring.NotificationWorkerMetrics
import java.time.Duration

/**
 * sender adapter 결과를 Micrometer metric으로 변환하는 decorator.
 *
 * channel 지원 여부와 발송 결과는 delegate 의미를 그대로 유지하며 계측 책임만 추가한다.
 * coroutine 취소는 sender 실패가 아니므로 기록하지 않고 호출자에게 전파한다.
 */
class MeteredNotificationSender(
    private val delegate: NotificationSender,
    private val metrics: NotificationWorkerMetrics,
) : NotificationSender {

    override fun supports(channel: NotificationChannel): Boolean {
        return delegate.supports(channel)
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        val startedAt = System.nanoTime()
        return try {
            val result = delegate.send(command)
            metrics.recordSender(
                channel = command.channel,
                result = result,
                elapsed = elapsed(startedAt),
            )
            result
        } catch (exception: CancellationException) {
            // 취소를 unexpected sender 장애로 기록하면 종료 시점의 실패율이 왜곡된다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordSenderUnexpected(
                channel = command.channel,
                elapsed = elapsed(startedAt),
            )
            throw exception
        }
    }

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }
}
