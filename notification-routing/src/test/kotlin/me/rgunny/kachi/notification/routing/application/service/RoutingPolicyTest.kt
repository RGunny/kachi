package me.rgunny.kachi.notification.routing.application.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("RoutingPolicy")
class RoutingPolicyTest {

    @Test
    @DisplayName("requester를 그대로 보관한다")
    fun keepRequester() {
        assertEquals("notification-routing", RoutingPolicy(requester = "notification-routing").requester)
    }

    @Test
    @DisplayName("requester가 비어 있으면 거부한다")
    fun rejectBlankRequester() {
        assertFailsWith<IllegalArgumentException> { RoutingPolicy(requester = " ") }
    }
}
