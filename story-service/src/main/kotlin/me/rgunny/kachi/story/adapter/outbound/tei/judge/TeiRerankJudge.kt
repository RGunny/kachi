package me.rgunny.kachi.story.adapter.outbound.tei.judge

import me.rgunny.kachi.story.adapter.outbound.tei.TeiClient
import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge

/** cross-encoder 판정기 서버로 [StoryLinkJudge]를 구현하는 adapter. */
class TeiRerankJudge(
    private val client: TeiClient
) : StoryLinkJudge {

    override val judge: StoryJudge = StoryJudge.BGE_RERANKER_V2_M3

    override suspend fun score(subject: EmbeddingText, candidates: List<JudgeCandidate>): List<Double> {
        if (candidates.isEmpty()) {
            return emptyList()
        }

        return client.rerank(query = subject.value, texts = candidates.map { it.text.value })
    }

    companion object {
        /** 이 adapter가 쓰는 판정기 가중치. */
        const val MODEL_ID = "BAAI/bge-reranker-v2-m3"
    }
}
