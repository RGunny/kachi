package me.rgunny.kachi.ai.support

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * stub 서버가 응답 순서와 요청 기록을 약속대로 지키는지 확인한다.
 */
class TestLlmServerTest {
    private val client = HttpClient.newHttpClient()

    @Test
    @DisplayName("큐에 넣은 순서대로 응답하고 큐가 비면 404를 돌려준다")
    fun respondInQueuedOrder() {
        TestLlmServer().use { llm ->
            llm.enqueueRateLimited(retryAfterSeconds = 3)
            llm.enqueueSummary(title = "제목", content = "본문", sentiment = "POSITIVE")

            val first = post(llm.baseUrl, """{"model":"m"}""")
            val second = post(llm.baseUrl, """{"model":"m"}""")
            val third = post(llm.baseUrl, """{"model":"m"}""")

            assertEquals(429, first.statusCode())
            assertEquals("3", first.headers().firstValue("Retry-After").get())
            assertEquals(200, second.statusCode())
            assertTrue(second.body().contains("\\\"sentiment\\\":\\\"POSITIVE\\\""))
            assertEquals(404, third.statusCode())
        }
    }

    @Test
    @DisplayName("요청 본문을 받은 순서대로 기록한다")
    fun recordRequestBodies() {
        TestLlmServer().use { llm ->
            llm.enqueueSummary()
            llm.enqueueSummary()

            post(llm.baseUrl, """{"model":"m","messages":[{"content":"NVIDIA"}]}""")
            post(llm.baseUrl, """{"model":"m","messages":[{"content":"TESLA"}]}""")

            assertEquals(2, llm.requests.size)
            assertTrue(llm.requests[0].contains("NVIDIA"))
            assertTrue(llm.requests[1].contains("TESLA"))
        }
    }

    @Test
    @DisplayName("고정 응답은 큐가 빈 뒤에도 계속 돌려준다")
    fun fallBackToFixedResponse() {
        TestStubServer().use { stub ->
            stub.respond("/api", TestStubResponse(statusCode = 200, body = """{"fixed":true}"""))
            stub.enqueue("/api", TestStubResponse(statusCode = 503, body = "{}"))

            assertEquals(503, get("${stub.baseUrl}/api?x=1").statusCode())
            assertEquals(200, get("${stub.baseUrl}/api").statusCode())
            assertEquals(200, get("${stub.baseUrl}/api").statusCode())
            assertEquals(3, stub.requestCount("/api"))
        }
    }

    private fun post(baseUrl: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl${TestLlmServer.CHAT_COMPLETIONS_PATH}"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(url: String): HttpResponse<String> {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
    }
}
