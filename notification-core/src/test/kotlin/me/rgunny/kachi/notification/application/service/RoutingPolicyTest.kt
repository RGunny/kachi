package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.NotificationChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@DisplayName("RoutingPolicy")
class RoutingPolicyTest {

    @Test
    @DisplayName("requester와 관리자 수신처 값은 비어 있을 수 없다")
    fun rejectBlank() {
        assertFailsWith<IllegalArgumentException> { RoutingPolicy(requester = " ", adminRecipients = emptyMap()) }
        assertFailsWith<IllegalArgumentException> {
            RoutingPolicy(requester = "routing", adminRecipients = mapOf(NotificationChannel.SLACK to ""))
        }
        RoutingPolicy(requester = "routing", adminRecipients = emptyMap())
    }
}
