package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RelayCollectorOutboxResult

/**
 * 항상 실패하는 relay 유스케이스 fake.
 *
 * executor가 실패한 뒤에도 lock을 돌려주는지, scheduler가 예외를 삼켜 다음 tick을 살려 두는지 확인하는 데 쓴다.
 */
class FailingRelayCollectorOutboxUseCase : RelayCollectorOutboxUseCase {
    var invokeCount = 0

    override suspend fun relay(): RelayCollectorOutboxResult {
        invokeCount += 1
        throw IllegalStateException("outbox relay failed")
    }
}
