package me.rgunny.kachi.story.adapter.outbound.tei

import org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * 추론 서버 하나의 `/health`를 actuator health에 싣는 indicator.
 *
 * 서버가 200이면 up, 그 밖의 응답이나 연결 실패는 down이다.
 */
class TeiHealthIndicator(
    private val webClient: WebClient,
    private val baseUrl: String
) : AbstractReactiveHealthIndicator() {

    override fun doHealthCheck(builder: Health.Builder): Mono<Health> {
        return webClient.get()
            .uri(HEALTH_PATH)
            .exchangeToMono { response ->
                val status = response.statusCode().value()
                val health = if (response.statusCode().is2xxSuccessful) builder.up() else builder.down()

                response.releaseBody().thenReturn(health.withDetail("url", baseUrl).withDetail("status", status).build())
            }
    }

    private companion object {
        const val HEALTH_PATH = "/health"
    }
}
