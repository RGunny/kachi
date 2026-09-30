package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.story.ApplyStoryMergeUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeResult

/**
 * 받은 병합 반영 명령을 기록하고 지정한 결과를 돌려주는 fake.
 */
class RecordingApplyStoryMergeUseCase : ApplyStoryMergeUseCase {

    val commands = mutableListOf<ApplyStoryMergeCommand>()
    var failure: Throwable? = null

    override suspend fun apply(command: ApplyStoryMergeCommand): ApplyStoryMergeResult {
        commands += command
        failure?.let { throw it }

        return ApplyStoryMergeResult(replayed = false, movedPendingCount = 0, summary = null)
    }
}
