package me.rgunny.kachi.story.adapter.inbound.outbox

import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * relay 요청의 중복 실행을 막고 유스케이스를 호출하는 executor.
 *
 * lock의 결과를 이 진입점의 언어로 옮기는 일만 한다.
 * relay 유스케이스 빈이 relay 설정에 조건부라 이 빈도 같은 조건으로만 등록된다.
 */
@Component
@ConditionalOnProperty(
    prefix = "kachi.story.outbox.relay",
    name = ["enabled"],
    havingValue = "true"
)
class StoryOutboxRelayExecutor(
    private val relayUseCase: RelayStoryOutboxUseCase,
    private val executionLock: ExecutionLockPort
) {
    suspend fun execute(): StoryOutboxRelayExecution {
        val outcome = executionLock.withLock(StoryExecutionLock.OUTBOX_RELAY) {
            relayUseCase.relay()
        }

        return when (outcome) {
            is ExecutedExecutionLockOutcome -> CompletedStoryOutboxRelayExecution(outcome.value)
            is AlreadyHeldExecutionLockOutcome -> AlreadyRunningStoryOutboxRelayExecution(outcome.holder)
            is UnavailableExecutionLockOutcome -> UnavailableStoryOutboxRelayExecution(outcome.cause)
        }
    }
}
