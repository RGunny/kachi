package me.rgunny.kachi.story.application.port.inbound.assembly

import me.rgunny.kachi.story.application.port.inbound.assembly.model.AssembleStoryResult
import me.rgunny.kachi.story.application.port.inbound.assembly.model.AttachArticleCommand

/**
 * 기사 한 건을 같은 사건의 story에 붙이거나 새 story를 여는 유스케이스.
 */
interface AssembleStoryUseCase {

    suspend fun assemble(command: AttachArticleCommand): AssembleStoryResult
}
