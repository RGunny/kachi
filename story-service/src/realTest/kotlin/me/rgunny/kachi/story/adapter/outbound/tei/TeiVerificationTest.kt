package me.rgunny.kachi.story.adapter.outbound.tei

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.tei.judge.TeiRerankJudge
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import me.rgunny.kachi.story.support.Goldset
import me.rgunny.kachi.story.support.TeiRealClients
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * 로컬 추론 서버 둘을 실제로 불러 모델 정체, 점수 공간, 골드셋 재현, 지연을 확인하는 테스트.
 *
 * 서버가 없으면 skip이 아니라 실패다.
 * 실행 전 `./scripts/infra.sh tei start`로 서버를 띄운다.
 */
@DisplayName("TEI 실호출 검증")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TeiVerificationTest {

    private val clients = TeiRealClients()
    private val model = clients.properties.embedding.model

    @Test
    @Order(1)
    @DisplayName("/info의 model_id·model_type이 enum과 맞다")
    fun infoMatchesEnum() = runBlocking {
        val embedding = TeiStartupProbe(clients.embedding, InferenceTarget.EMBEDDING, model.modelId).verify()
        val judge = TeiStartupProbe(clients.judge, InferenceTarget.JUDGE, TeiRerankJudge.MODEL_ID).verify()

        assertTrue(clients.properties.embedding.batchSize <= assertNotNull(embedding.max_client_batch_size))
        report("info", "embedding model=${embedding.model_id} sha=${embedding.model_sha} dtype=${embedding.model_dtype} maxInputLength=${embedding.max_input_length} maxClientBatchSize=${embedding.max_client_batch_size}")
        report("info", "judge model=${judge.model_id} sha=${judge.model_sha} dtype=${judge.model_dtype} maxInputLength=${judge.max_input_length} maxClientBatchSize=${judge.max_client_batch_size}")
    }

    @Test
    @Order(2)
    @DisplayName("임베딩은 1024차원 단위 벡터이고 자기 코사인은 1이다")
    fun embeddingIsUnitVector() = runBlocking {
        val vectors = clients.embedding.embed(listOf(SAMPLE_A, SAMPLE_B))

        vectors.forEach { vector ->
            assertEquals(model.dimension, vector.size)
            assertTrue(vector.all { it.isFinite() })
            val norm = sqrt(vector.sumOf { it.toDouble() * it })
            assertTrue(abs(norm - 1.0) < 1e-3, "norm=$norm")
        }
        val a = Embedding.of(model, vectors[0])
        val b = Embedding.of(model, vectors[1])
        assertTrue(abs(a.cosine(a) - 1.0) < 1e-5)
        report("embed", "dimension=${vectors[0].size} selfCosine=${"%.6f".format(a.cosine(a))} crossCosine=${"%.4f".format(a.cosine(b))}")
    }

    @Test
    @Order(3)
    @DisplayName("같은 텍스트 쌍의 판정 점수가 0.99 이상이라 raw_scores=false는 sigmoid 공간이다")
    fun rerankSelfIsNearOne() = runBlocking {
        val scores = clients.judge.rerank(SAMPLE_A, listOf(SAMPLE_A, SAMPLE_B))

        assertTrue(scores[0] >= 0.99, "self score=${scores[0]}")
        report("rerank", "self=${"%.4f".format(scores[0])} other=${"%.4f".format(scores[1])}")
    }

    /**
     * 실험이 DJL로 낸 코사인·판정 점수를 같은 입력으로 다시 내어 대조한다.
     * 두 텍스트의 합이 512토큰을 넘는 쌍은 따로 센다.
     */
    @Test
    @Order(4)
    @DisplayName("골드셋 250쌍의 코사인·판정 점수가 실험 값과 허용 차 안에서 같다")
    fun reproduceGoldset() = runBlocking {
        val pairs = Goldset.pairs()
        val expected = Goldset.scores()
        assertEquals(pairs.size, expected.size)

        val texts = pairs.flatMap { listOf(it.a, it.b) }.associate { it.newsId to it.embeddingText }
        val ids = texts.keys.toList()
        val vectors = mutableMapOf<String, Embedding>()
        val embedTime = measureTime {
            ids.chunked(clients.properties.embedding.batchSize).forEach { batch ->
                clients.embedding.embed(batch.map { texts.getValue(it) }).forEachIndexed { i, vector ->
                    vectors[batch[i]] = Embedding.of(model, vector)
                }
            }
        }
        val tokenCounts = mutableMapOf<String, Int>()
        ids.chunked(clients.properties.embedding.batchSize).forEach { batch ->
            clients.embedding.countTokens(batch.map { texts.getValue(it) }).forEachIndexed { i, count -> tokenCounts[batch[i]] = count }
        }

        val judged = mutableMapOf<String, Double>()
        val judgeTime = measureTime {
            pairs.groupBy { it.a.newsId }.values.forEach { group ->
                group.chunked(MAX_RERANK_TEXTS).forEach { chunk ->
                    val scores = clients.judge.rerank(chunk.first().a.embeddingText, chunk.map { it.b.embeddingText })
                    chunk.forEachIndexed { i, pair -> judged[pair.pairId] = scores[i] }
                }
            }
        }

        var cosineWithin = 0
        var judgeWithin = 0
        var maxCosineDiff = 0.0
        var maxJudgeDiff = 0.0
        val worst = mutableListOf<String>()
        val overLengthInputs = ids.count { tokenCounts.getValue(it) > MAX_INPUT_TOKENS }
        var overLengthPairs = 0
        val predictions = mutableMapOf<String, Boolean>()
        pairs.forEach { pair ->
            val djl = expected.getValue(pair.pairId)
            val cosine = vectors.getValue(pair.a.newsId).cosine(vectors.getValue(pair.b.newsId))
            val judge = judged.getValue(pair.pairId)
            val cosineDiff = abs(cosine - djl.cosine)
            val judgeDiff = abs(judge - djl.judge)
            if (cosineDiff <= COSINE_TOLERANCE) cosineWithin++
            if (judgeDiff <= JUDGE_TOLERANCE) judgeWithin++
            maxCosineDiff = maxOf(maxCosineDiff, cosineDiff)
            maxJudgeDiff = maxOf(maxJudgeDiff, judgeDiff)
            val jointTokens = tokenCounts.getValue(pair.a.newsId) + tokenCounts.getValue(pair.b.newsId)
            if (jointTokens > MAX_INPUT_TOKENS) overLengthPairs++
            if (cosineDiff > COSINE_TOLERANCE || judgeDiff > JUDGE_TOLERANCE) {
                worst += "${pair.pairId} cosine=${"%.4f".format(cosine)}/${"%.4f".format(djl.cosine)} judge=${"%.4f".format(judge)}/${"%.4f".format(djl.judge)} jointTokens=$jointTokens"
            }
            predictions[pair.pairId] = sameStory(cosine, judge)
        }

        val labeled = pairs.filter { it.label != null }
        val tp = labeled.count { it.label == true && predictions.getValue(it.pairId) }
        val fp = labeled.count { it.label == false && predictions.getValue(it.pairId) }
        val fn = labeled.count { it.label == true && !predictions.getValue(it.pairId) }
        val precision = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)

        report("goldset", "pairs=${pairs.size} uniqueTexts=${ids.size} embedTime=${embedTime} judgeTime=${judgeTime}")
        report("goldset", "cosineWithin${COSINE_TOLERANCE}=${cosineWithin}/${pairs.size} maxCosineDiff=${"%.4f".format(maxCosineDiff)}")
        report("goldset", "judgeWithin${JUDGE_TOLERANCE}=${judgeWithin}/${pairs.size} maxJudgeDiff=${"%.4f".format(maxJudgeDiff)}")
        report("goldset", "inputsOver${MAX_INPUT_TOKENS}Tokens=$overLengthInputs pairsOver${MAX_INPUT_TOKENS}JointTokens=$overLengthPairs")
        report("goldset", "theta high=$THETA_HIGH low=$THETA_LOW judge=$THETA_JUDGE precision=${"%.3f".format(precision)} recall=${"%.3f".format(recall)} tp=$tp fp=$fp fn=$fn labeled=${labeled.size}")
        worst.forEach { report("goldset-diff", it) }

        val required = (pairs.size * REQUIRED_RATIO).toInt()
        assertTrue(cosineWithin >= required, "코사인 허용 차 안 쌍이 $cosineWithin/${pairs.size}로 ${REQUIRED_RATIO}에 못 미칩니다")
        assertTrue(judgeWithin >= required, "판정 허용 차 안 쌍이 $judgeWithin/${pairs.size}로 ${REQUIRED_RATIO}에 못 미칩니다. dtype(float16) 영향인지 float32로 다시 잰다")
    }

    @Test
    @Order(5)
    @DisplayName("batch 32 임베딩과 후보 10 판정의 지연을 보고한다")
    fun reportLatency() = runBlocking {
        val texts = Goldset.pairs().flatMap { listOf(it.a.embeddingText, it.b.embeddingText) }.distinct()
        val batch = texts.take(clients.properties.embedding.batchSize)
        val candidates = texts.drop(batch.size).take(RERANK_CANDIDATES)

        val embedLatencies = (1..LATENCY_SAMPLES).map { measureTime { clients.embedding.embed(batch) } }
        val judgeLatencies = (1..LATENCY_SAMPLES).map { measureTime { clients.judge.rerank(batch.first(), candidates) } }
        val singleEmbed = (1..LATENCY_SAMPLES).map { measureTime { clients.embedding.embed(listOf(batch.first())) } }

        report("latency", "embed batch=${batch.size} ${percentiles(embedLatencies)}")
        report("latency", "embed batch=1 ${percentiles(singleEmbed)}")
        report("latency", "rerank candidates=${candidates.size} ${percentiles(judgeLatencies)}")
        report("latency", "local yaml embedding timeout=${clients.properties.embedding.timeout} slowAfter=${clients.properties.embedding.slowAfter} judge timeout=${clients.properties.judge.timeout} slowAfter=${clients.properties.judge.slowAfter}")
    }

    /**
     * 조립 규칙 ③. 실험의 `EvaluateThresholds`와 같은 함수다.
     */
    private fun sameStory(cosine: Double, judge: Double): Boolean {
        return when {
            cosine >= THETA_HIGH -> true
            cosine >= THETA_LOW -> judge >= THETA_JUDGE
            else -> false
        }
    }

    private fun percentiles(samples: List<Duration>): String {
        val sorted = samples.sorted()
        val p50 = sorted[(sorted.size - 1) / 2]
        val p95 = sorted[minOf(sorted.size - 1, (sorted.size * 0.95).toInt())]

        return "n=${sorted.size} p50=${p50} p95=${p95} max=${sorted.last()}"
    }

    private fun report(event: String, detail: String) {
        println("[realTest] $event $detail")
    }

    private companion object {
        const val SAMPLE_A = "NVIDIA reports record data center revenue\n엔비디아가 데이터센터 매출 신기록을 발표했다"
        const val SAMPLE_B = "Iran warns Korea over Hormuz deployment\n이란이 호르무즈 파병을 검토하는 한국에 경고했다"
        const val COSINE_TOLERANCE = 0.01
        const val JUDGE_TOLERANCE = 0.02
        const val REQUIRED_RATIO = 0.99
        const val MAX_INPUT_TOKENS = 512
        const val MAX_RERANK_TEXTS = 32
        const val RERANK_CANDIDATES = 10
        const val LATENCY_SAMPLES = 5
        const val THETA_HIGH = 0.70
        const val THETA_LOW = 0.60
        const val THETA_JUDGE = 0.30
    }
}
