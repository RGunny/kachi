package me.rgunny.kachi.story.adapter.outbound.tei.judge

import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge
import me.rgunny.kachi.story.fake.FakeTeiClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TeiRerankJudge")
class TeiRerankJudgeTest {
    private val client = FakeTeiClient()
    private val judge = TeiRerankJudge(client)

    @Test
    @DisplayName("후보 텍스트 전부를 한 호출에 보내고 후보 순서로 점수를 돌려준다")
    fun scoreInOneCall() = runBlocking {
        val subject = EmbeddingText.of("제목", "발췌문")
        val candidates = listOf(
            JudgeCandidate(EmbeddingText.of("다른 제목", "다른 발췌문"), 0.62),
            JudgeCandidate(subject, 0.9)
        )

        val scores = judge.score(subject, candidates)

        assertEquals(listOf(0.25, 1.0), scores)
        assertEquals(1, client.rerankCallCount)
        assertEquals(subject.value to candidates.map { it.text.value }, client.rerankedQueries.single())
    }

    @Test
    @DisplayName("후보가 비면 호출하지 않는다")
    fun emptyCandidates() = runBlocking {
        assertEquals(emptyList(), judge.score(EmbeddingText.of("a", "b"), emptyList()))
        assertEquals(0, client.rerankCallCount)
    }

    @Test
    @DisplayName("판정기 정체는 cross-encoder다")
    fun identity() {
        assertEquals(StoryJudge.BGE_RERANKER_V2_M3, judge.judge)
        assertEquals("BAAI/bge-reranker-v2-m3", TeiRerankJudge.MODEL_ID)
    }
}
