package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RelayAiOutboxResult
import me.rgunny.kachi.ai.fixture.AiTestFixture

/**
 * 호출 횟수만 기록하고 지정한 집계를 돌려주는 relay 유스케이스 fake.
 *
 * scheduler가 집계에 따라 로그를 가르는지 확인하는 데 쓴다.
 */
class RecordingRelayAiOutboxUseCase(
    var result: RelayAiOutboxResult = emptyResult()
) : RelayAiOutboxUseCase {
    var invokeCount = 0

    override suspend fun relay(): RelayAiOutboxResult {
        invokeCount += 1

        return result
    }

    companion object {

        fun emptyResult(): RelayAiOutboxResult {
            return RelayAiOutboxResult(
                processed = 0,
                published = 0,
                retried = 0,
                dead = 0,
                staleRecovered = 0,
                completedAt = AiTestFixture.NOW
            )
        }
    }
}
