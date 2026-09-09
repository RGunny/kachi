package me.rgunny.kachi.story.domain

/**
 * 유사도가 θ_high 이상이라 판정기 없이 붙인 판정.
 */
data class AutoMergedLinkDecision(
    val storyId: StoryId,
    override val similarity: Double
) : LinkDecision {

    init {
        require(similarity in -1.0..1.0) { "코사인 유사도는 -1과 1 사이여야 합니다: $similarity" }
    }

    override val merged: Boolean
        get() = true

    override val candidateStoryId: StoryId
        get() = storyId
}
