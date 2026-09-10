package me.rgunny.kachi.story.adapter.outbound.judge

import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge

/**
 * 후보의 코사인 유사도를 판정 점수로 그대로 돌려주는 [StoryLinkJudge].
 */
class ThresholdOnlyStoryLinkJudge : StoryLinkJudge {

    override val judge: StoryJudge = StoryJudge.THRESHOLD_ONLY

    override suspend fun score(subject: EmbeddingText, candidates: List<JudgeCandidate>): List<Double> {
        return candidates.map { it.similarity }
    }
}
