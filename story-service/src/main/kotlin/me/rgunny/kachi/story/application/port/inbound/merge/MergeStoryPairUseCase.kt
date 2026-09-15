package me.rgunny.kachi.story.application.port.inbound.merge

import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairCommand
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairResult

/**
 * 운영자가 지정한 OPEN story 쌍을 합치는 유스케이스.
 */
interface MergeStoryPairUseCase {

    suspend fun mergeStoryPair(command: MergeStoryPairCommand): MergeStoryPairResult
}
