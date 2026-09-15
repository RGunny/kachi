package me.rgunny.kachi.story.adapter.inbound.index

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rgunny.kachi.story.application.port.inbound.index.RebuildCandidateIndexUseCase
import me.rgunny.kachi.story.application.port.inbound.index.model.RebuildCandidateIndexResult
import me.rgunny.kachi.story.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutedExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component

/**
 * 색인 재구축의 중복 실행을 막고 본체는 요청 밖에서 돌리는 실행기.
 *
 * 요청은 lock 획득까지만 기다리고 재구축 완료는 로그로 남긴다. scheduler 스레드를 쓰지 않는다.
 */
@Component
class IndexRebuildExecutor(
    private val rebuildCandidateIndexUseCase: RebuildCandidateIndexUseCase,
    private val executionLock: ExecutionLockPort
) : DisposableBean {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun start(): IndexRebuildExecution {
        val started = CompletableDeferred<IndexRebuildExecution>()
        scope.launch {
            runCatching {
                executionLock.withLock(StoryExecutionLock.INDEX_REBUILD) {
                    started.complete(StartedIndexRebuildExecution)
                    rebuildCandidateIndexUseCase.rebuild()
                }
            }.onSuccess { outcome ->
                when (outcome) {
                    is ExecutedExecutionLockOutcome -> logFinished(outcome.value)
                    is AlreadyHeldExecutionLockOutcome -> started.complete(AlreadyRunningIndexRebuildExecution(outcome.holder))
                    is UnavailableExecutionLockOutcome -> started.complete(UnavailableIndexRebuildExecution(outcome.cause))
                }
            }.onFailure { error ->
                started.complete(UnavailableIndexRebuildExecution(error))
                log.error("Index rebuild failed", error)
            }
        }

        return started.await()
    }

    override fun destroy() {
        scope.cancel()
    }

    private fun logFinished(result: RebuildCandidateIndexResult) {
        log.info(
            "Index rebuild finished: scanned={}, indexed={}, skippedClosed={}",
            result.scannedCount,
            result.indexedCount,
            result.skippedClosedCount
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(IndexRebuildExecutor::class.java)
    }
}
