package me.rgunny.kachi.ai.adapter.inbound.outbox

import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.config.AiOutboxRelayProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * relay 실행 요청을 받아 같은 인스턴스 안의 tick 겹침을 막고 유스케이스를 호출한다.
 *
 * 여러 인스턴스가 같은 행을 동시에 다루는 것은 저장소의 조건부 쓰기가 막는다.
 * 여기서 막는 것은 앞선 tick이 끝나기 전에 다음 tick이 시작되어 같은 행을 두 번 조회하는 낭비다.
 */
@Component
@ConditionalOnProperty(
    prefix = AiOutboxRelayProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class AiOutboxRelayExecutor(
    private val relayUseCase: RelayAiOutboxUseCase,
    private val clock: Clock
) {
    private val runningRelay = AtomicReference<RunningAiOutboxRelay?>(null)

    suspend fun execute(): AiOutboxRelayExecutionResult {
        // 1. 현재 요청이 lock을 획득했을 때 기록할 실행 시작 메타데이터를 만든다.
        val currentRelay = RunningAiOutboxRelay(
            startedAt = Instant.now(clock)
        )

        // 2. 이미 relay가 실행 중이면 유스케이스를 호출하지 않고 중복 실행 결과를 반환한다.
        // 확인하는 사이에 앞선 tick이 끝나 비어 있을 수 있으므로 그때는 현재 요청의 시각을 대신 알린다.
        if (!runningRelay.compareAndSet(null, currentRelay)) {
            return AiOutboxRelayAlreadyRunning(
                runningRelay = runningRelay.get() ?: currentRelay
            )
        }

        // 3. lock을 획득한 요청만 실제 relay 유스케이스를 실행한다.
        return try {
            AiOutboxRelayFinished(relayUseCase.relay())
        } finally {
            // 4. 성공/실패와 무관하게 다음 실행을 받을 수 있도록 lock을 해제한다.
            runningRelay.compareAndSet(currentRelay, null)
        }
    }
}
