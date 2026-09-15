package me.rgunny.kachi.story.adapter.outbound.tei

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/** 추론 서버 호출 중 발생한 client-level 예외의 timeout 여부를 판단하는 classifier. */
object TeiHttpExceptionClassifier {

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
