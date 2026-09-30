package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind
import me.rgunny.kachi.ai.domain.summary.StorySummaryId

/**
 * 요약 버전이 새로 저장된 결과.
 *
 * [published]는 발행 대기 이벤트가 outbox에 함께 남았다는 뜻이다.
 */
data class CreatedStorySummaryResult(
    val summaryId: StorySummaryId,
    val storyId: StoryId,
    val version: Long,
    val developmentKind: StoryDevelopmentKind,
    val newArticleCount: Int,
    val published: Boolean
) : SummarizeStoryResult
