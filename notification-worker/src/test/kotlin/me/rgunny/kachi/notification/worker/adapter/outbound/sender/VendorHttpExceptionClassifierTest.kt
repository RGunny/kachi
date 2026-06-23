package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("VendorHttpExceptionClassifier")
class VendorHttpExceptionClassifierTest {

    @Test
    @DisplayName("cause chain의 timeout 예외를 timeout으로 분류한다")
    fun timeoutCause() {
        val exceptions = listOf(
            SocketTimeoutException("socket timeout"),
            ConnectTimeoutException("connect timeout"),
            ReadTimeoutException.INSTANCE,
            WriteTimeoutException.INSTANCE,
            TimeoutException("reactor timeout"),
        )

        exceptions.forEach { timeout ->
            val wrapped = RuntimeException("vendor request failed", IllegalStateException("nested", timeout))

            assertTrue(
                VendorHttpExceptionClassifier.isTimeout(wrapped),
                "expected timeout for ${timeout::class.qualifiedName}",
            )
        }
    }

    @Test
    @DisplayName("timeout이 아닌 일반 예외는 timeout으로 분류하지 않는다")
    fun notTimeout() {
        val exception = RuntimeException("vendor request failed", IllegalArgumentException("bad request"))

        assertFalse(VendorHttpExceptionClassifier.isTimeout(exception))
    }
}
