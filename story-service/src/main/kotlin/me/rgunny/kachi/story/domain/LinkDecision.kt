package me.rgunny.kachi.story.domain

/**
 * 기사 한 건을 어느 story에 붙일지 정한 판정의 기록.
 */
sealed interface LinkDecision {
    val merged: Boolean
    val candidateStoryId: StoryId?
    val similarity: Double?
}
