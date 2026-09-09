package me.rgunny.kachi.story.domain

/** 새 story를 연 결과. */
data class NewStoryLinkDecision(
    override val candidateStoryId: StoryId?,
    override val similarity: Double?
) : LinkDecision {

    init {
        require((candidateStoryId == null) == (similarity == null)) { "최고 후보와 유사도는 함께 있거나 함께 없어야 합니다" }
        require(similarity == null || similarity in -1.0..1.0) { "코사인 유사도는 -1과 1 사이여야 합니다: $similarity" }
    }

    override val merged: Boolean
        get() = false
}
