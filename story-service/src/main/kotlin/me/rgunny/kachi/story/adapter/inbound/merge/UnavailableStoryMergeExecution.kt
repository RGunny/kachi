package me.rgunny.kachi.story.adapter.inbound.merge

/**
 * lock을 확인할 수 없어 병합을 시작하지 않은 결과.
 */
data class UnavailableStoryMergeExecution(
    val cause: Throwable
) : StoryMergeExecution
