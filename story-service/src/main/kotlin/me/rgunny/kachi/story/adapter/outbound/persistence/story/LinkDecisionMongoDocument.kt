package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.util.UUID
import me.rgunny.kachi.story.domain.AutoMergedLinkDecision
import me.rgunny.kachi.story.domain.JudgedLinkDecision
import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.NewStoryLinkDecision
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryJudge

/**
 * 기사 문서에 내장되는 판정 기록.
 *
 * 세 판정은 [type]으로 구분하고, 판정마다 쓰지 않는 필드는 null이다.
 */
data class LinkDecisionMongoDocument(
    val type: String,
    val candidateStoryId: UUID?,
    val similarity: Double?,
    val judge: String?,
    val judgeScore: Double?,
    val merged: Boolean
) {

    fun toDomain(): LinkDecision {
        return when (type) {
            TYPE_AUTO_MERGED -> AutoMergedLinkDecision(
                storyId = StoryId.of(required(candidateStoryId, "candidateStoryId")),
                similarity = required(similarity, "similarity")
            )

            TYPE_JUDGED -> JudgedLinkDecision(
                candidateStoryId = StoryId.of(required(candidateStoryId, "candidateStoryId")),
                similarity = required(similarity, "similarity"),
                judge = StoryJudge.ofCode(required(judge, "judge")),
                judgeScore = required(judgeScore, "judgeScore"),
                merged = merged
            )

            TYPE_NEW_STORY -> NewStoryLinkDecision(
                candidateStoryId = candidateStoryId?.let { StoryId.of(it) },
                similarity = similarity
            )

            else -> throw IllegalStateException("알 수 없는 판정 종류입니다: $type")
        }
    }

    private fun <T : Any> required(value: T?, field: String): T {
        return checkNotNull(value) { "$type 판정에는 $field 값이 있어야 합니다" }
    }

    companion object {
        const val TYPE_AUTO_MERGED = "AUTO_MERGED"
        const val TYPE_JUDGED = "JUDGED"
        const val TYPE_NEW_STORY = "NEW_STORY"

        fun fromDomain(decision: LinkDecision): LinkDecisionMongoDocument {
            return when (decision) {
                is AutoMergedLinkDecision -> LinkDecisionMongoDocument(
                    type = TYPE_AUTO_MERGED,
                    candidateStoryId = decision.storyId.value,
                    similarity = decision.similarity,
                    judge = null,
                    judgeScore = null,
                    merged = true
                )

                is JudgedLinkDecision -> LinkDecisionMongoDocument(
                    type = TYPE_JUDGED,
                    candidateStoryId = decision.candidateStoryId.value,
                    similarity = decision.similarity,
                    judge = decision.judge.code,
                    judgeScore = decision.judgeScore,
                    merged = decision.merged
                )

                is NewStoryLinkDecision -> LinkDecisionMongoDocument(
                    type = TYPE_NEW_STORY,
                    candidateStoryId = decision.candidateStoryId?.value,
                    similarity = decision.similarity,
                    judge = null,
                    judgeScore = null,
                    merged = false
                )
            }
        }
    }
}
