package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.adapter.outbound.llm.LlmHttpExceptionClassifier
import me.rgunny.kachi.ai.support.TestStubResponse
import me.rgunny.kachi.ai.support.TestStubServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 제공자 층과 모델 층의 값이 각자 자리에 붙는지 실제 HTTP로 보는 테스트.
 */
@DisplayName("LlmWebClients")
class LlmWebClientsTest {

    private val server = TestStubServer()

    @AfterEach
    fun close() {
        server.close()
    }

    @Test
    @DisplayName("api key가 있으면 제공자 WebClient가 모든 요청에 Bearer 헤더를 싣는다")
    fun bearerHeaderPerProvider() {
        server.respond(PATH, ok())
        val webClient = LlmWebClients.forProvider(baseUrl = server.baseUrl, apiKey = "api-key", connectTimeout = CONNECT)

        webClient.get().uri(PATH).retrieve().bodyToMono<String>().block()

        assertEquals("Bearer api-key", server.requestHeaders.single()["authorization"])
    }

    @Test
    @DisplayName("api key가 없으면 Authorization 헤더를 싣지 않는다")
    fun omitBearerHeaderWithoutApiKey() {
        server.respond(PATH, ok())
        val webClient = LlmWebClients.forProvider(baseUrl = server.baseUrl, apiKey = "", connectTimeout = CONNECT)

        webClient.get().uri(PATH).retrieve().bodyToMono<String>().block()

        assertFalse(server.requestHeaders.single().containsKey("authorization"))
    }

    /**
     * 같은 제공자 WebClient에서 나온 두 모델 WebClient 중 timeout이 짧은 쪽만 실패해야 한다.
     */
    @Test
    @DisplayName("모델 WebClient는 제공자의 연결 설정을 물려받고 응답 timeout만 자기 값으로 갖는다")
    fun responseTimeoutPerModel() {
        server.respond(PATH, ok(delay = Duration.ofMillis(600)))
        val provider = LlmWebClients.forProvider(baseUrl = server.baseUrl, apiKey = "api-key", connectTimeout = CONNECT)
        val impatient = LlmWebClients.forModel(provider, responseTimeout = Duration.ofMillis(150))
        val patient = LlmWebClients.forModel(provider, responseTimeout = Duration.ofSeconds(5))

        // block()은 TimeoutException을 ReactiveException으로 감싼다. adapter가 보는 것은 cause 체인이고, 분류기도 체인을 훑는다.
        val exception = assertFailsWith<RuntimeException> {
            impatient.get().uri(PATH).retrieve().bodyToMono<String>().block()
        }
        val body = patient.get().uri(PATH).retrieve().bodyToMono<String>().block()

        assertIs<TimeoutException>(exception.cause)
        assertTrue(LlmHttpExceptionClassifier.isTimeout(exception))
        assertEquals("{}", body)
        assertEquals("Bearer api-key", server.requestHeaders.last()["authorization"])
    }

    private fun ok(delay: Duration = Duration.ZERO): TestStubResponse {
        return TestStubResponse(statusCode = 200, body = "{}", delay = delay)
    }

    private companion object {
        const val PATH = "/models"
        val CONNECT: Duration = Duration.ofSeconds(2)
    }
}
