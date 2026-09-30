package me.rgunny.kachi.story.adapter.inbound.close

/**
 * lock을 확인할 수 없어 닫기를 시작하지 않은 결과.
 */
data class UnavailableStoryCloseExecution(
    val cause: Throwable
) : StoryCloseExecution
