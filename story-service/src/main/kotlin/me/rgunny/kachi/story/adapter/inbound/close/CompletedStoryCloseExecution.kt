package me.rgunny.kachi.story.adapter.inbound.close

import me.rgunny.kachi.story.application.port.inbound.close.model.CloseIdleStoriesResult

/**
 * lock을 획득해 닫기가 실행된 결과.
 */
data class CompletedStoryCloseExecution(
    val result: CloseIdleStoriesResult
) : StoryCloseExecution
