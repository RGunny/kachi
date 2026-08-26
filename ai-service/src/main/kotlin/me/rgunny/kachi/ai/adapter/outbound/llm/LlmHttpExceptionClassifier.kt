package me.rgunny.kachi.ai.adapter.outbound.llm

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * LLM provider 호출 중 발생한 client-level 예외를 공통 분류한다.
 *
 * provider별 HTTP status/body 해석은 adapter에 두고, 여기서는 Netty/WebClient 계층의 timeout 성격만 판단한다.
 * timeout은 예외 체인의 어느 깊이에서든 나올 수 있어 cause를 끝까지 훑는다.
 */
object LlmHttpExceptionClassifier {

    fun isTimeout(exception: Throwable): Boolean {
        return exception.hasCauseMatching {
            it is SocketTimeoutException ||
                it is ConnectTimeoutException ||
                it is ReadTimeoutException ||
                it is WriteTimeoutException ||
                it is TimeoutException
        }
    }

    private fun Throwable.hasCauseMatching(predicate: (Throwable) -> Boolean): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (predicate(current)) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
