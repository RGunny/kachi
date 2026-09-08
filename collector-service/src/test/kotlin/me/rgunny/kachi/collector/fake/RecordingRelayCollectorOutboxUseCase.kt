package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RelayCollectorOutboxResult
import me.rgunny.kachi.collector.fixture.CollectorTestFixture

/**
 * 호출 횟수만 기록하고 지정한 집계를 돌려주는 relay 유스케이스 fake.
 *
 * scheduler가 집계에 따라 로그를 가르는지 확인하는 데 쓴다.
 */
class RecordingRelayCollectorOutboxUseCase(
    var result: RelayCollectorOutboxResult = emptyResult()
) : RelayCollectorOutboxUseCase {
    var invokeCount = 0

    override suspend fun relay(): RelayCollectorOutboxResult {
        invokeCount += 1

        return result
    }

    companion object {

        fun emptyResult(): RelayCollectorOutboxResult {
            return RelayCollectorOutboxResult(
                processed = 0,
                published = 0,
                retried = 0,
                dead = 0,
                staleRecovered = 0,
                completedAt = CollectorTestFixture.NOW
            )
        }
    }
}
