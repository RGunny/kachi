package me.rgunny.kachi.story.adapter.inbound.index

/**
 * lock을 확인할 수 없어 색인 재구축을 시작하지 않은 결과.
 */
data class UnavailableIndexRebuildExecution(
    val cause: Throwable
) : IndexRebuildExecution
