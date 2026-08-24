package me.rgunny.kachi.ai.fake

import kotlinx.coroutines.CompletableDeferred
import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RelayAiOutboxResult

/**
 * [release]가 완료될 때까지 실행 중 상태를 유지하는 relay 유스케이스 fake.
 *
 * 중복 실행 방지 lock은 "실행이 겹치는 순간"에만 관찰되므로, 실행을 붙잡아 둘 수단이 필요하다.
 */
class BlockingRelayAiOutboxUseCase : RelayAiOutboxUseCase {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var executeCount = 0

    override suspend fun relay(): RelayAiOutboxResult {
        executeCount += 1
        started.complete(Unit)
        release.await()

        return RecordingRelayAiOutboxUseCase.emptyResult()
    }
}
