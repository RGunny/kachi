package me.rgunny.kachi.ai.application.port.outbound.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.summary.NewsSummary

/**
 * 뉴스 요약 결과 저장소 출력 포트
 */
interface NewsSummaryPersistencePort {

    suspend fun findByUniqueKey(
        keyword: AiKeyword,
        newsHash: String,
        promptVersion: PromptVersion
    ): NewsSummary?

    suspend fun save(newsSummary: NewsSummary): NewsSummary

    /**
     * 요약과 발행 대기 이벤트를 한 트랜잭션으로 저장한다.
     *
     * 같은 요약이 이미 있으면 기존 요약을 반환하고 이벤트는 남기지 않는다.
     */
    suspend fun saveOrFindExisting(newsSummary: NewsSummary, outbox: AiOutbox): NewsSummary
}
