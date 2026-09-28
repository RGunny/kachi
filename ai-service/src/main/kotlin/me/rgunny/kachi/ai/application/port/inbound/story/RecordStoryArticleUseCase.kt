package me.rgunny.kachi.ai.application.port.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleResult

/**
 * story에 붙은 기사를 사본으로 기록하고, 요약 트리거를 충족하면 그 자리에서 요약까지 실행하는 유스케이스.
 */
interface RecordStoryArticleUseCase {

    suspend fun record(command: RecordStoryArticleCommand): RecordStoryArticleResult
}
