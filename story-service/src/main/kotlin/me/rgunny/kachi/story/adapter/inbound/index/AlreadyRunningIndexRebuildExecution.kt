package me.rgunny.kachi.story.adapter.inbound.index

import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder

/**
 * 다른 색인 재구축이 이미 실행 중이라 이번 요청을 건너뛴 결과.
 */
data class AlreadyRunningIndexRebuildExecution(
    val holder: ExecutionLockHolder
) : IndexRebuildExecution
