package me.rgunny.kachi.ai.application.port.inbound.news

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsResult

/**
 * 키워드별 저장 뉴스를 요약하는 입력 포트
 */
interface SummarizeNewsUseCase {

    suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult
}
