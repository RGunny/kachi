package me.rgunny.kachi.story.support

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer

/**
 * 테스트 대상이 HTTP로 부르는 상대(추론 서버 등)를 대신하는 테스트 JVM 안의 서버.
 *
 * path마다 응답 큐를 둔다.
 * 큐가 비면 [respond]로 둔 고정 응답을 돌려주고, 그것도 없으면 404다.
 * 요청 path와 본문을 기록한다.
 */
class TestStubServer : AutoCloseable {
    private val queuedResponses = ConcurrentHashMap<String, ConcurrentLinkedQueue<TestStubResponse>>()
    private val fixedResponses = ConcurrentHashMap<String, TestStubResponse>()

    private val server: DisposableServer = HttpServer.create()
        // 0번 포트는 OS가 빈 포트를 고르게 한다. 병렬 실행이나 로컬 고정 포트와 충돌하지 않는다.
        .port(0)
        .handle { request, response ->
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
                .then(Mono.delay(stubResponse.delay))
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

    /** 다음 테스트가 이전 테스트의 응답과 기록을 물려받지 않도록 비운다. */
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
