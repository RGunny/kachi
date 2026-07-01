package me.rgunny.kachi.notification.worker.support

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Slack/Discord/Telegram sender 통합 테스트에서 실제 외부 vendor 대신 사용하는 로컬 HTTP 서버.
 *
 * 테스트는 운영 sender/client/WebClient 조립을 그대로 사용하되, webhook URL이나 Telegram baseUrl만 이 서버로 돌린다.
 * 이렇게 하면 실제 Slack/Discord/Telegram으로 메시지를 보내지 않고도 sender 라우팅, HTTP status 분류,
 * retry-after header/body 처리, listener ack/retry 예외 변환까지 같은 경로로 검증할 수 있다.
 *
 * path별 응답은 생성자 `responses`로 지정한다.
 * 지정하지 않은 path는 각 vendor의 일반 성공 응답에 맞춘 기본 응답을 돌려준다.
 * 요청된 path는 `paths`에 기록해서 mock sender가 아니라 real sender가 호출됐는지 검증할 수 있다.
 */
class TestVendorServer(
    responses: Map<String, TestVendorResponse> = emptyMap(),
) : AutoCloseable {
    private val configuredResponses = ConcurrentHashMap(responses)
    private val server: DisposableServer = HttpServer.create() // netty server
        // 0번 포트는 OS가 사용 가능한 임의 포트를 할당하게 한다.
        // 테스트 병렬 실행이나 로컬 개발환경의 고정 포트 충돌을 피하기 위함이다.
        .port(0)
        .handle { request, response ->
            // Query string은 sender 라우팅 검증에 필요하지 않으므로 path만 비교한다.
            // Telegram처럼 path 뒤에 query가 붙는 client가 생겨도 응답 매칭을 안정적으로 유지한다.
            val path = request.uri().substringBefore("?")
            paths += path
            val vendorResponse = configuredResponses[path] ?: defaultResponse(path)
            response.status(HttpResponseStatus.valueOf(vendorResponse.statusCode))
            vendorResponse.headers.forEach { (name, value) -> response.header(name, value) }
            response.header(HttpHeaderNames.CONTENT_TYPE, "application/json")
            response.sendString(Mono.just(vendorResponse.body))
        }
        // bindNow()가 호출되는 시점에 실제 localhost 서버 소켓이 열린다.
        // 이 객체가 생성된 뒤에는 baseUrl로 WebClient 요청을 받을 수 있다.
        .bindNow()

    val baseUrl: String = "http://localhost:${server.port()}"
    val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun close() {
        // use 블록 종료 시 테스트용 서버를 즉시 내려 포트와 Netty 리소스를 반환한다.
        server.disposeNow()
    }

    private fun defaultResponse(path: String): TestVendorResponse {
        // vendor별 성공 응답 형태가 달라 기본값도 sender가 성공으로 분류할 수 있는 형태로 맞춘다.
        return when {
            path == "/discord" -> TestVendorResponse(statusCode = 204, body = "")
            path.startsWith("/bot") -> TestVendorResponse(statusCode = 200, body = """{"ok":true}""")
            else -> TestVendorResponse(statusCode = 200, body = "ok")
        }
    }
}

/**
 * TestVendorServer가 특정 path에 반환할 HTTP 응답 정의.
 */
data class TestVendorResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
)
