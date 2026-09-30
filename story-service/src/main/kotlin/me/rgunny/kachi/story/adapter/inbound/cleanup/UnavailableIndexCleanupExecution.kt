package me.rgunny.kachi.story.adapter.inbound.cleanup

/**
 * lock을 확인할 수 없어 색인 정리를 시작하지 않은 결과.
 */
data class UnavailableIndexCleanupExecution(
    val cause: Throwable
) : IndexCleanupExecution
