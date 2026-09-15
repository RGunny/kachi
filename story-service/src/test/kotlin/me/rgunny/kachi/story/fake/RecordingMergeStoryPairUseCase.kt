package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.merge.MergeStoryPairUseCase
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairCommand
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeStoryPairResult

/**
 * 병합 명령을 기록하고 정해 둔 결과나 실패를 돌려주는 운영자 병합 유스케이스.
 */
class RecordingMergeStoryPairUseCase : MergeStoryPairUseCase {
    var result: MergeStoryPairResult? = null
    var failure: Throwable? = null
    var lastCommand: MergeStoryPairCommand? = null

    override suspend fun mergeStoryPair(command: MergeStoryPairCommand): MergeStoryPairResult {
        lastCommand = command
        failure?.let { throw it }

        return requireNotNull(result) { "돌려줄 결과를 지정하지 않았습니다" }
    }
}
