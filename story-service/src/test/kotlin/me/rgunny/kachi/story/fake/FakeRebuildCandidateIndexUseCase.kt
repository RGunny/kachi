package me.rgunny.kachi.story.fake

import kotlinx.coroutines.CompletableDeferred
import me.rgunny.kachi.story.application.port.inbound.index.RebuildCandidateIndexUseCase
import me.rgunny.kachi.story.application.port.inbound.index.model.RebuildCandidateIndexResult

/**
 * gate가 열릴 때까지 멈춰 있다가 정해 둔 결과를 돌려주는 색인 재구축 유스케이스.
 *
 * 즉시 반환이 필요한 테스트는 gate를 미리 연다.
 */
class FakeRebuildCandidateIndexUseCase : RebuildCandidateIndexUseCase {
    val gate = CompletableDeferred<Unit>()
    val completed = CompletableDeferred<RebuildCandidateIndexResult>()
    val result = RebuildCandidateIndexResult(scannedCount = 1, indexedCount = 1, skippedClosedCount = 0)

    var callCount: Int = 0
        private set

    override suspend fun rebuild(): RebuildCandidateIndexResult {
        callCount += 1
        gate.await()
        completed.complete(result)

        return result
    }
}
