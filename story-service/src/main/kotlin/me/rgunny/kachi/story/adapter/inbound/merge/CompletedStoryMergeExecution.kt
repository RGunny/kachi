package me.rgunny.kachi.story.adapter.inbound.merge

import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeOpenStoriesResult

/**
 * lock을 획득해 병합이 실행된 결과.
 */
data class CompletedStoryMergeExecution(
    val result: MergeOpenStoriesResult
) : StoryMergeExecution
