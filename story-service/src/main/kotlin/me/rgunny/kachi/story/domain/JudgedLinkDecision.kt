package me.rgunny.kachi.story.domain

/** 회색 구간에서 judge가 정한 결과. */
data class JudgedLinkDecision(
    override val candidateStoryId: StoryId,
    override val similarity: Double,
    val judge: StoryJudge,
    val judgeScore: Double,
    override val merged: Boolean
) : LinkDecision {

    init {
        require(similarity in -1.0..1.0) { "코사인 유사도는 -1과 1 사이여야 합니다: $similarity" }
        require(judgeScore in 0.0..1.0) { "판정 점수는 0과 1 사이여야 합니다: $judgeScore" }
    }
}
