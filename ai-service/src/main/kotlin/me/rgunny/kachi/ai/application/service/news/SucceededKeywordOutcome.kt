package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizedNewsResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata

internal data class SucceededKeywordOutcome(
    val summary: SummarizedNewsResult,
    // 실행 기록에는 이번 실행이 어떤 provider/model을 썼는지도 남아야 한다.
    val metadata: LlmGenerationMetadata
) : KeywordOutcome
