package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * 기사 사본 기록의 결과.
 *
 * [storyId]는 기사가 귀속된 story이고, 흡수된 story로 온 기사는 명령의 값과 다를 수 있다.
 * [replayed]는 같은 기사가 이미 기록되어 있었다는 뜻이다.
 * [summary]는 이 기록이 즉시 트리거를 충족해 실행된 요약의 결과이고, 트리거가 없었으면 null이다.
 */
data class RecordStoryArticleResult(
    val storyId: StoryId,
    val replayed: Boolean,
    val summary: SummarizeStoryResult?
)
