package me.rgunny.kachi.ai.application.port.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult

/**
 * story 하나의 미요약 기사를 직전 버전 요약과 함께 LLM에 넣어 다음 버전을 만드는 유스케이스.
 */
interface SummarizeStoryUseCase {

    suspend fun summarize(command: SummarizeStoryCommand): SummarizeStoryResult
}
