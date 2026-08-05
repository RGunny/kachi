package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Vendor HTTP 호출 중 발생한 client-level 예외를 공통 분류한다.
 *
 * 각 vendor의 HTTP status/body 해석은 sender별로 유지하고, 여기서는 Netty/WebClient 계층의
 * 공통 timeout 성격만 판단한다.
 */
object VendorHttpExceptionClassifier {

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
