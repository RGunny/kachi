package me.rgunny.kachi.story.fake

import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import me.rgunny.kachi.story.adapter.outbound.tei.TeiClient
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiInfoResponse
import me.rgunny.kachi.story.support.TeiInfoJson
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 호출을 기록하고 정해 둔 실패·응답을 돌려주는 추론 서버 클라이언트.
 *
 * [failures]에 쌓인 예외를 호출마다 하나씩 던지고, 비면 결정적 벡터와 점수를 돌려준다.
 */
class FakeTeiClient(
    private val dimension: Int = 4,
    var info: TeiInfoResponse = embeddingInfo()
) : TeiClient {

    val failures: ArrayDeque<Throwable> = ArrayDeque()
    var callDelay: Duration = Duration.ZERO
    var gate: CompletableDeferred<Unit>? = null
    var embedCallCount: Int = 0
        private set
    var rerankCallCount: Int = 0
        private set
    var infoCallCount: Int = 0
        private set
    val embeddedBatches: MutableList<List<String>> = mutableListOf()
    val rerankedQueries: MutableList<Pair<String, List<String>>> = mutableListOf()

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        embedCallCount += 1
        embeddedBatches += texts
        beforeAnswer()

        return texts.map { text -> FloatArray(dimension) { i -> ((text.hashCode() shr i) and 0xff) / 255f } }
    }

    override suspend fun rerank(query: String, texts: List<String>): List<Double> {
        rerankCallCount += 1
        rerankedQueries += query to texts
        beforeAnswer()

        return texts.map { text -> if (text == query) 1.0 else 0.25 }
    }

    override suspend fun info(): TeiInfoResponse {
        infoCallCount += 1
        failures.removeFirstOrNull()?.let { throw it }

        return info
    }

    private suspend fun beforeAnswer() {
        gate?.await()
        if (!callDelay.isZero) {
            delay(callDelay.toMillis())
        }
        failures.removeFirstOrNull()?.let { throw it }
    }

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

        fun embeddingInfo(): TeiInfoResponse = JSON.readValue(TeiInfoJson.EMBEDDING, TeiInfoResponse::class.java)

        fun rerankerInfo(): TeiInfoResponse = JSON.readValue(TeiInfoJson.RERANKER, TeiInfoResponse::class.java)
    }
}
