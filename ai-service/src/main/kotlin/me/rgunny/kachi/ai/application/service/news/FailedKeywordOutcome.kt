package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.domain.run.AiFailureReason

/**
 * [abortsRun]은 이 실패가 남은 키워드까지 확정적으로 막는 전역 장애인지를 나타낸다.
 * 참이면 이번 실행은 남은 키워드를 호출하지 않고 건너뛴다(ADR 021).
 */
internal data class FailedKeywordOutcome(
    val reason: AiFailureReason,
    val abortsRun: Boolean
) : KeywordOutcome
