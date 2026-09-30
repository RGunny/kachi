package me.rgunny.kachi.notification.worker.adapter.inbound.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.inbound.dispatch.RecoverStaleProcessingDispatchUseCase
import me.rgunny.kachi.notification.worker.adapter.monitoring.NotificationWorkerMetrics
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * PROCESSING 상태로 멈춘 dispatch를 주기적으로 회수하는 scheduler adapter.
 *
 * 실제 stale 판단과 상태 전이는 core의 RecoverStaleProcessingDispatchUseCase가 담당한다.
 * scheduler는 주기적 trigger와 운영 로그만 담당한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "kachi.notification.dispatch.recovery",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class NotificationDispatchRecoveryScheduler(
    private val recoverUseCase: RecoverStaleProcessingDispatchUseCase,
    private val metrics: NotificationWorkerMetrics,
) {

    /**
     * scheduler tick마다 core recovery use case를 호출하고 운영 로그를 남긴다.
     */
    @Scheduled(fixedDelayString = "\${kachi.notification.dispatch.recovery.interval}")
    fun recoverStaleProcessing() = runBlocking {
        val startedAt = System.nanoTime()
        // 1. scheduler는 trigger만 담당하고 stale 판단과 상태 전이는 core use case에 맡긴다.
        val result = try {
            recoverUseCase.recoverStaleProcessing()
        } catch (exception: CancellationException) {
            // 애플리케이션 종료 취소는 다음 tick에서 복구할 업무 실패가 아니다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordProcessingRecoveryFailure(elapsed(startedAt))
            // 2. recovery tick 실패는 다음 tick에서 다시 시도할 수 있도록
            // scheduler 자체를 중단시키지 않는다.
            log.error(
                "notification dispatch recovery tick failed. next scheduler tick will retry stale processing notifications",
                exception,
            )
            return@runBlocking
        }
        metrics.recordProcessingRecovery(result, elapsed(startedAt))

        if (result.staleProcessingFound > 0) {
            // 3. 운영자가 회수 규모와 DEAD 전환 여부를 추적할 수 있게 처리된 tick만 남긴다.
            log.info(
                "notification dispatch recovery tick staleProcessingFound={} staleProcessingRecovered={} recoveredToRetryWait={} recoveredToDead={} staleProcessingSkipped={} recoveryTickCompletedAt={}",
                result.staleProcessingFound,
                result.staleProcessingRecovered,
                result.recoveredToRetryWait,
                result.recoveredToDead,
                result.staleProcessingSkipped,
                result.recoveryTickCompletedAt,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(NotificationDispatchRecoveryScheduler::class.java)
    }

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }
}
