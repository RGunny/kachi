package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.split.SplitStoryUseCase
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryCommand
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryResult

/**
 * 분리 명령을 기록하고 정해 둔 결과나 실패를 돌려주는 분리 유스케이스.
 */
class RecordingSplitStoryUseCase : SplitStoryUseCase {
    var result: SplitStoryResult? = null
    var failure: Throwable? = null
    var lastCommand: SplitStoryCommand? = null

    override suspend fun split(command: SplitStoryCommand): SplitStoryResult {
        lastCommand = command
        failure?.let { throw it }

        return requireNotNull(result) { "돌려줄 결과를 지정하지 않았습니다" }
    }
}
