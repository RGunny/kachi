package me.rgunny.kachi.story.application.port.inbound.split

import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryCommand
import me.rgunny.kachi.story.application.port.inbound.split.model.SplitStoryResult

/**
 * OPEN story에서 지정한 기사를 새 story로 떼어 내는 유스케이스.
 */
interface SplitStoryUseCase {

    suspend fun split(command: SplitStoryCommand): SplitStoryResult
}
