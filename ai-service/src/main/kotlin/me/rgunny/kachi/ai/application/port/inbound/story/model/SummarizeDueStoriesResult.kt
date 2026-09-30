package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * maxWait를 넘긴 story들을 훑은 tick 하나의 결과.
 *
 * [aborted]는 호출할 수 있는 LLM 모델이 하나도 없어 남은 story를 건너뛰었다는 뜻이다.
 */
data class SummarizeDueStoriesResult(
    val due: Int,
    val created: Int,
    val skipped: Int,
    val failed: Int,
    val aborted: Boolean
)
