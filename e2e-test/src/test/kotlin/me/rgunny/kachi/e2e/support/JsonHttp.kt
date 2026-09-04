package me.rgunny.kachi.e2e.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 서비스 API를 부르는 작은 HTTP 클라이언트. 응답은 상태 코드와 JSON 트리로 돌려준다.
 */
class JsonHttp(
    // 계약 타입은 Kotlin data class라 Kotlin 모듈이 있어야 역직렬화된다.
    val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun get(url: String, bearer: String? = null): JsonHttpResponse {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), bearer)
    }

    fun post(url: String, body: Any? = null, bearer: String? = null): JsonHttpResponse {
        return send(HttpRequest.newBuilder(URI.create(url)).POST(publisher(body)), bearer)
    }

    fun put(url: String, body: Any? = null, bearer: String? = null): JsonHttpResponse {
        return send(HttpRequest.newBuilder(URI.create(url)).PUT(publisher(body)), bearer)
    }

    fun getText(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun publisher(body: Any?): HttpRequest.BodyPublisher {
        return if (body == null) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body))
        }
    }

    private fun send(builder: HttpRequest.Builder, bearer: String?): JsonHttpResponse {
        builder.header("Content-Type", "application/json").timeout(Duration.ofSeconds(10))
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        val json = if (body.isBlank()) jsonMapper.createObjectNode() else jsonMapper.readTree(body)
        return JsonHttpResponse(response.statusCode(), json, body)
    }
}

/**
 * [JsonHttp] 응답. `expect`는 기대한 상태 코드가 아니면 본문을 포함한 메시지로 실패시킨다.
 */
data class JsonHttpResponse(val status: Int, val json: JsonNode, val rawBody: String) {
    fun expect(vararg statuses: Int): JsonHttpResponse {
        check(status in statuses) { "expected status ${statuses.toList()} but was $status: $rawBody" }
        return this
    }

    val data: JsonNode get() = json.path("data")
}
