package me.rgunny.kachi.story.adapter.outbound.tei

import java.time.Duration
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.config.TeiServerProperties
import me.rgunny.kachi.story.config.TeiWebClients
import me.rgunny.kachi.story.domain.inference.InferenceFailureAttribution
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import me.rgunny.kachi.story.support.TeiInfoJson
import me.rgunny.kachi.story.support.TestStubResponse
import me.rgunny.kachi.story.support.TestStubServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.databind.json.JsonMapper

@DisplayName("TeiHttpClient")
class TeiHttpClientTest {

    @BeforeEach
    fun resetStub() {
        stub.reset()
    }

    @Test
    @DisplayName("임베딩 요청을 /embed로 보내고 순서대로 벡터를 돌려준다")
    fun embed() = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, ok("[[0.1, 0.2], [0.3, 0.4]]"))

        val vectors = client(dimension = 2).embed(listOf("a", "b"))

        assertEquals(2, vectors.size)
        assertContentEquals(floatArrayOf(0.1f, 0.2f), vectors[0])
        assertContentEquals(floatArrayOf(0.3f, 0.4f), vectors[1])
        val body = JSON.readTree(stub.bodies.getValue(TeiHttpClient.EMBED_PATH).single())
        val inputs: List<String> = body.path("inputs").values().map { it.asString() }
        assertEquals(listOf("a", "b"), inputs)
        assertEquals(true, body.path("normalize").asBoolean())
        assertEquals(true, body.path("truncate").asBoolean())
    }

    @Test
    @DisplayName("판정 요청을 /rerank로 보내고 점수순 응답을 입력 순서로 되돌린다")
    fun rerank() = runBlocking {
        stub.respond(TeiHttpClient.RERANK_PATH, ok("""[{"index":1,"score":0.9},{"index":0,"score":0.1}]"""))

        val scores = client(target = InferenceTarget.JUDGE).rerank("q", listOf("x", "y"))

        assertEquals(listOf(0.1, 0.9), scores)
        val body = JSON.readTree(stub.bodies.getValue(TeiHttpClient.RERANK_PATH).single())
        assertEquals("q", body.path("query").asString())
        val texts: List<String> = body.path("texts").values().map { it.asString() }
        assertEquals(listOf("x", "y"), texts)
        assertEquals(false, body.path("raw_scores").asBoolean())
        assertEquals(true, body.path("truncate").asBoolean())
    }

    @Test
    @DisplayName("/info의 실제 응답을 DTO로 읽는다")
    fun info() = runBlocking {
        stub.respond(TeiHttpClient.INFO_PATH, ok(TeiInfoJson.EMBEDDING))

        val info = client().info()

        assertEquals("BAAI/bge-m3", info.model_id)
        assertEquals("cls", info.model_type?.embedding?.pooling)
        assertEquals(32, info.max_client_batch_size)
    }

    @Test
    @DisplayName("/tokenize 응답의 토큰 수를 입력마다 돌려준다")
    fun countTokens() = runBlocking {
        stub.respond(
            TeiHttpClient.TOKENIZE_PATH,
            ok("""[[{"id":0,"text":"<s>","special":true},{"id":5,"text":"a","special":false}],[{"id":0,"text":"<s>","special":true}]]""")
        )

        val counts = client().countTokens(listOf("a", ""))

        assertEquals(listOf(2, 1), counts)
        val body = JSON.readTree(stub.bodies.getValue(TeiHttpClient.TOKENIZE_PATH).single())
        assertEquals(true, body.path("add_special_tokens").asBoolean())
    }

    @ParameterizedTest
    @CsvSource(
        "400, INFERENCE_INPUT_EMPTY",
        "413, INFERENCE_PAYLOAD_TOO_LARGE",
        "422, INFERENCE_INPUT_INVALID",
        "424, INFERENCE_FAILED",
        "429, INFERENCE_OVERLOADED",
        "503, INFERENCE_UNHEALTHY",
        "500, INFERENCE_SERVER_ERROR",
        "502, INFERENCE_SERVER_ERROR",
        "409, INFERENCE_UNKNOWN_ERROR"
    )
    @DisplayName("HTTP status를 실패 코드로 옮기고 status와 대상을 보존한다")
    fun classifyHttpStatus(status: Int, code: InferenceFailureCode) = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, TestStubResponse(statusCode = status, body = """{"error":"x","error_type":"y"}"""))

        val exception = assertFailsWith<InferenceException> { client().embed(listOf("a")) }

        assertEquals(code, exception.failure.code)
        assertEquals(status, exception.failure.statusCode)
        assertEquals(InferenceTarget.EMBEDDING, exception.failure.target)
        assertTrue(exception.failure.message.contains("status=$status"))
    }

    @Test
    @DisplayName("응답 timeout을 넘기면 TIMEOUT으로 분류한다")
    fun classifyTimeout() = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, ok("[[0.1, 0.2]]", delay = Duration.ofSeconds(2)))

        val exception = assertFailsWith<InferenceException> { client(timeout = Duration.ofMillis(300)).embed(listOf("a")) }

        assertEquals(InferenceFailureCode.INFERENCE_TIMEOUT, exception.failure.code)
        assertTrue(exception.failure.transient)
    }

    @Test
    @DisplayName("연결할 수 없으면 NETWORK_ERROR로 분류한다")
    fun classifyConnectionFailure() = runBlocking {
        val closed = java.net.ServerSocket(0).use { it.localPort }

        val exception = assertFailsWith<InferenceException> { client(baseUrl = "http://localhost:$closed").embed(listOf("a")) }

        assertEquals(InferenceFailureCode.INFERENCE_NETWORK_ERROR, exception.failure.code)
        assertEquals(InferenceFailureAttribution.SERVER, exception.failure.attribution)
    }

    @Test
    @DisplayName("벡터 차원이 모델과 다르면 MODEL 귀속의 INVALID_RESPONSE다")
    fun classifyDimensionMismatch() = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, ok("[[0.1, 0.2, 0.3]]"))

        val exception = assertFailsWith<InferenceException> { client(dimension = 2).embed(listOf("a")) }

        assertEquals(InferenceFailureCode.INFERENCE_INVALID_RESPONSE, exception.failure.code)
        assertEquals(InferenceFailureAttribution.MODEL, exception.failure.attribution)
        assertTrue(!exception.failure.transient)
    }

    @Test
    @DisplayName("벡터 개수가 입력 수와 다르면 INVALID_RESPONSE다")
    fun classifyCountMismatch() = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, ok("[[0.1, 0.2]]"))

        val exception = assertFailsWith<InferenceException> { client(dimension = 2).embed(listOf("a", "b")) }

        assertEquals(InferenceFailureCode.INFERENCE_INVALID_RESPONSE, exception.failure.code)
    }

    @Test
    @DisplayName("JSON이 아닌 200 응답은 INVALID_RESPONSE다")
    fun classifyUndecodableBody() = runBlocking {
        stub.respond(TeiHttpClient.EMBED_PATH, ok("not json"))

        val exception = assertFailsWith<InferenceException> { client().embed(listOf("a")) }

        assertEquals(InferenceFailureCode.INFERENCE_INVALID_RESPONSE, exception.failure.code)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "index 누락|[{\"score\":0.9},{\"index\":0,\"score\":0.1}]",
            "index 중복|[{\"index\":0,\"score\":0.9},{\"index\":0,\"score\":0.1}]",
            "index 범위 밖|[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.1}]",
            "개수 부족|[{\"index\":0,\"score\":0.9}]",
            "score 누락|[{\"index\":1},{\"index\":0,\"score\":0.1}]",
            "sigmoid 범위 밖|[{\"index\":1,\"score\":1.5},{\"index\":0,\"score\":0.1}]"
        ]
    )
    @DisplayName("판정 응답의 index·score가 계약을 어기면 INVALID_RESPONSE다")
    fun classifyInvalidRerankResponse(case: String, body: String) = runBlocking {
        stub.respond(TeiHttpClient.RERANK_PATH, ok(body))

        val exception = assertFailsWith<InferenceException>(case) { client(target = InferenceTarget.JUDGE).rerank("q", listOf("x", "y")) }

        assertEquals(InferenceFailureCode.INFERENCE_INVALID_RESPONSE, exception.failure.code, case)
        assertEquals(InferenceTarget.JUDGE, exception.failure.target)
    }

    @Test
    @DisplayName("빈 입력은 호출 없이 거부한다")
    fun rejectEmptyInput() = runBlocking {
        val client = client()

        assertFailsWith<IllegalArgumentException> { client.embed(emptyList()) }
        assertFailsWith<IllegalArgumentException> { client.rerank("q", emptyList()) }
        assertEquals(0, stub.paths.size)
    }

    private fun client(
        dimension: Int? = null,
        target: InferenceTarget = InferenceTarget.EMBEDDING,
        timeout: Duration = Duration.ofSeconds(5),
        baseUrl: String = stub.baseUrl
    ): TeiHttpClient {
        val server = TeiServerProperties(
            baseUrl = baseUrl,
            connectTimeout = Duration.ofSeconds(1),
            timeout = timeout,
            slowAfter = timeout.dividedBy(2)
        )

        return TeiHttpClient(
            webClient = TeiWebClients.forServer(server),
            target = target,
            expectedDimension = dimension
        )
    }

    private fun ok(body: String, delay: Duration = Duration.ZERO): TestStubResponse {
        return TestStubResponse(statusCode = 200, body = body, delay = delay)
    }

    companion object {
        private val stub = TestStubServer()
        private val JSON: JsonMapper = JsonMapper.builder().build()

        @JvmStatic
        @AfterAll
        fun closeStub() {
            stub.close()
        }
    }
}
