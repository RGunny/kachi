package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge

/**
 * 후보 텍스트마다 지정한 점수를 돌려주는 판정기.
 *
 * 지정이 없으면 [defaultScore]다.
 */
class FakeStoryLinkJudge(
    override val judge: StoryJudge = StoryJudge.BGE_RERANKER_V2_M3,
    var defaultScore: Double = 0.0
) : StoryLinkJudge {

    val scores: MutableMap<EmbeddingText, Double> = mutableMapOf()
    val scoredSubjects: MutableList<EmbeddingText> = mutableListOf()
    val scoredCandidates: MutableList<List<JudgeCandidate>> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun score(subject: EmbeddingText, candidates: List<JudgeCandidate>): List<Double> {
        scoredSubjects += subject
        scoredCandidates += candidates
        failure?.let { throw it }

        return candidates.map { scores[it.text] ?: defaultScore }
    }
}
