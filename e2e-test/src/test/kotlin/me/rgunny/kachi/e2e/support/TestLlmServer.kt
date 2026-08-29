package me.rgunny.kachi.e2e.support

import tools.jackson.databind.json.JsonMapper

/**
 * OpenAI 호환 chat completions API를 흉내 내는 LLM provider stub.
 *
 * [TestStubServer] 위에서 `/chat/completions` 한 path만 다룬다.
 * 요약 응답은 provider가 파싱하는 JSON(title·content·sentiment)을 content 문자열에 담아 돌려주고,
 * 실패는 status와 `Retry-After`만으로 표현한다. provider가 어떤 실패로 분류하는지는 provider의 몫이다.
 */
class TestLlmServer : AutoCloseable {
    private val server = TestStubServer()
    private val jsonMapper = JsonMapper.builder().build()

    val baseUrl: String get() = server.baseUrl
    val port: Int get() = server.port

    /** provider가 보낸 chat completions 요청 본문. 호출 횟수와 프롬프트 내용 단언에 쓴다. */
    val requests: List<String>
        get() = server.bodies[CHAT_COMPLETIONS_PATH].orEmpty().toList()

    fun enqueueSummary(
        title: String = "요약 제목",
        content: String = "요약 본문",
        sentiment: String = "NEUTRAL"
    ) {
        val summaryJson = jsonMapper.writeValueAsString(
            mapOf("title" to title, "content" to content, "sentiment" to sentiment)
        )
        server.enqueue(CHAT_COMPLETIONS_PATH, completion(summaryJson))
    }

    /** 큐가 비어도 계속 같은 요약을 돌려준다. 어느 키워드가 몇 번 요약될지 정하지 않는 시나리오에 쓴다. */
    fun respondSummary(
        title: String = "요약 제목",
        content: String = "요약 본문",
        sentiment: String = "NEUTRAL"
    ) {
        val summaryJson = jsonMapper.writeValueAsString(
            mapOf("title" to title, "content" to content, "sentiment" to sentiment)
        )
        server.respond(CHAT_COMPLETIONS_PATH, completion(summaryJson))
    }

    fun enqueueRateLimited(retryAfterSeconds: Long) {
        server.enqueue(
            CHAT_COMPLETIONS_PATH,
            TestStubResponse(
                statusCode = 429,
                headers = mapOf("Retry-After" to retryAfterSeconds.toString()),
                body = """{"error":{"message":"rate limited"}}"""
            )
        )
    }

    fun enqueueClientError() {
        server.enqueue(
            CHAT_COMPLETIONS_PATH,
            TestStubResponse(statusCode = 400, body = """{"error":{"message":"bad request"}}""")
        )
    }

    fun enqueueServerError() {
        server.enqueue(
            CHAT_COMPLETIONS_PATH,
            TestStubResponse(statusCode = 500, body = """{"error":{"message":"internal error"}}""")
        )
    }

    fun reset() {
        server.reset()
    }

    override fun close() {
        server.close()
    }

    private fun completion(content: String): TestStubResponse {
        val body = jsonMapper.writeValueAsString(
            mapOf(
                "model" to "test-model",
                "choices" to listOf(mapOf("message" to mapOf("role" to "assistant", "content" to content))),
                "usage" to mapOf("prompt_tokens" to 10, "completion_tokens" to 20)
            )
        )
        return TestStubResponse(statusCode = 200, body = body)
    }

    companion object {
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
    }
}
