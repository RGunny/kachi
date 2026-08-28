package me.rgunny.kachi.notification.routing.application.service

import me.rgunny.kachi.notification.contract.NotificationChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("RoutingRequestId")
class RoutingRequestIdTest {

    @Test
    @DisplayName("요약 requestId는 sum:{summaryId}:u:{userId}:c:{CHANNEL}이다")
    fun forSummary() {
        assertEquals(
            "sum:summary-1:u:user-1:c:SLACK",
            RoutingRequestId.forSummary("summary-1", "user-1", NotificationChannel.SLACK),
        )
    }

    @Test
    @DisplayName("격리 requestId는 qrt:{eventKey}:u:{recipientId}:c:{CHANNEL}이다")
    fun forQuarantine() {
        assertEquals(
            "qrt:quarantine-1:1700000000000:u:admin-1:c:TELEGRAM",
            RoutingRequestId.forQuarantine("quarantine-1:1700000000000", "admin-1", NotificationChannel.TELEGRAM),
        )
    }

    @Test
    @DisplayName("같은 입력은 같은 requestId를 만든다")
    fun deterministic() {
        val first = RoutingRequestId.forSummary("summary-1", "user-1", NotificationChannel.DISCORD)
        val second = RoutingRequestId.forSummary("summary-1", "user-1", NotificationChannel.DISCORD)

        assertEquals(first, second)
    }
}
