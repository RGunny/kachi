package me.rgunny.kachi.ai.application.port.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeResult

/**
 * story 병합 이벤트를 받아 흡수된 story의 미요약 기사와 상태를 흡수한 story로 옮기는 유스케이스.
 */
interface ApplyStoryMergeUseCase {

    suspend fun apply(command: ApplyStoryMergeCommand): ApplyStoryMergeResult
}
