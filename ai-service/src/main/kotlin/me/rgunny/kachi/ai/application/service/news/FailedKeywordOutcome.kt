package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.domain.run.AiFailureReason

/**
 * 키워드 하나의 실패 결과.
 * 호출할 수 있는 모델이 하나도 없어 실제 호출이 나가지 않았을 때 참이고, 이번 실행은 남은 키워드를 호출하지 않고 건너뛴다.
 */
data class FailedKeywordOutcome(
    val reason: AiFailureReason,
    val abortsRun: Boolean
) : KeywordOutcome
