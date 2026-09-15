package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.story.domain.AutoMergedLinkDecision
import me.rgunny.kachi.story.domain.JudgedLinkDecision
import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.NewStoryLinkDecision

/**
 * 기사 한 건의 판정 기록 응답.
 *
 * [judge]·[judgeScore]는 판정기를 거친 판정에만 있다.
 */
data class LinkDecisionResponse(
    val kind: String,
    val merged: Boolean,
    val candidateStoryId: UUID?,
    val similarity: Double?,
    val judge: String?,
    val judgeScore: Double?
) {
    companion object {

        fun from(decision: LinkDecision): LinkDecisionResponse {
            return when (decision) {
                is NewStoryLinkDecision -> LinkDecisionResponse(
                    kind = "NEW_STORY",
                    merged = decision.merged,
                    candidateStoryId = decision.candidateStoryId?.value,
                    similarity = decision.similarity,
                    judge = null,
                    judgeScore = null
                )

                is AutoMergedLinkDecision -> LinkDecisionResponse(
                    kind = "AUTO_MERGED",
                    merged = decision.merged,
                    candidateStoryId = decision.candidateStoryId.value,
                    similarity = decision.similarity,
                    judge = null,
                    judgeScore = null
                )

                is JudgedLinkDecision -> LinkDecisionResponse(
                    kind = "JUDGED",
                    merged = decision.merged,
                    candidateStoryId = decision.candidateStoryId.value,
                    similarity = decision.similarity,
                    judge = decision.judge.name,
                    judgeScore = decision.judgeScore
                )
            }
        }
    }
}
