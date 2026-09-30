package me.rgunny.kachi.notification.routing.support

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 테스트 대상이 HTTP로 부르는 상대를 대신하는 테스트 JVM 안의 서버.
 *
 * 같은 JVM의 서비스는 [baseUrl]로, 컨테이너 안의 서비스는 `host.testcontainers.internal:[port]`로 이 서버에 닿는다.
 *
 * 서비스의 WebClient 조립은 그대로 두고 base-url만 이 서버로 돌린다.
 * path마다 응답 큐를 두어 "처음은 429, 다음은 200"처럼 호출 순서에 따라 다른 응답을 표현한다.
 * 큐가 비면 [respond]로 둔 고정 응답을 돌려주고, 그것도 없으면 404다.
 * 요청 path와 본문은 [paths]와 [bodies]에 기록한다.
 */
class TestStubServer : AutoCloseable {
    private val queuedResponses = ConcurrentHashMap<String, ConcurrentLinkedQueue<TestStubResponse>>()
    private val fixedResponses = ConcurrentHashMap<String, TestStubResponse>()

    private val server: DisposableServer = HttpServer.create()
        // OS가 고르는 빈 포트
        .port(0)
        .handle { request, response ->
            // 응답 조회 키(query 제외 path)
            val path = request.uri().substringBefore("?")
            paths += path
            val stubResponse = queuedResponses[path]?.poll()
                ?: fixedResponses[path]
                ?: NOT_FOUND
            response.status(HttpResponseStatus.valueOf(stubResponse.statusCode))
            stubResponse.headers.forEach { (name, value) -> response.header(name, value) }
            response.header(HttpHeaderNames.CONTENT_TYPE, "application/json")
            request.receive().aggregate().asString().defaultIfEmpty("")
                .doOnNext { body -> bodies.getOrPut(path) { Collections.synchronizedList(mutableListOf()) } += body }
                .then(response.sendString(Mono.just(stubResponse.body)).then())
        }
        .bindNow()

    val port: Int = server.port()
    val baseUrl: String = "http://localhost:$port"
    val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val bodies: MutableMap<String, MutableList<String>> = ConcurrentHashMap()

    /** 한 번만 쓰이는 응답을 path의 큐 뒤에 붙인다. */
    fun enqueue(path: String, response: TestStubResponse) {
        queuedResponses.getOrPut(path) { ConcurrentLinkedQueue() } += response
    }

    /** 큐가 비었을 때 계속 돌려줄 응답을 둔다. */
    fun respond(path: String, response: TestStubResponse) {
        fixedResponses[path] = response
    }

    fun requestCount(path: String): Int {
        return synchronized(paths) { paths.count { it == path } }
    }

    /** 응답 큐·고정 응답·요청 기록을 전부 비운다. */
    fun reset() {
        queuedResponses.clear()
        fixedResponses.clear()
        paths.clear()
        bodies.clear()
    }

    override fun close() {
        server.disposeNow()
    }

    private companion object {
        val NOT_FOUND = TestStubResponse(statusCode = 404, body = "{}")
    }
}
