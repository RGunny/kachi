package me.rgunny.kachi.notification.routing.config

import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("NotificationRoutingProperties")
class NotificationRoutingPropertiesTest {

    @Test
    @DisplayName("yaml 키가 바인딩된다")
    fun bind() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "kachi.notification.routing.group-id" to "notification-routing",
                "kachi.notification.routing.request-topic" to "notification.requested",
                "kachi.notification.routing.auto-offset-reset" to "earliest",
                "kachi.notification.routing.topics.summary-created" to "ai.summary.created",
                "kachi.notification.routing.topics.keyword-quarantined" to "ai.keyword.quarantined",
                "kachi.notification.routing.dlt.topic" to "notification.routing.dlt",
                "kachi.notification.routing.retry.max-attempts" to "3",
                "kachi.notification.routing.retry.backoff" to "1s",
                "kachi.notification.routing.admin.recipients.slack" to "admin",
                "kachi.notification.routing.admin.recipients.telegram" to "admin-chat",
                "kachi.notification.routing.user-service.base-url" to "http://localhost:8080",
                "kachi.notification.routing.user-service.subscriptions-path" to "/api/v1/internal/subscriptions",
                "kachi.notification.routing.user-service.timeout" to "3s",
                "kachi.notification.routing.user-service.max-in-memory-size" to "262144",
            )
        )

        val properties = Binder(source)
            .bind(NotificationRoutingProperties.PREFIX, NotificationRoutingProperties::class.java)
            .get()

        assertEquals("notification-routing", properties.groupId)
        assertEquals("notification.requested", properties.requestTopic)
        assertEquals("earliest", properties.autoOffsetReset)
        assertEquals("ai.summary.created", properties.topics.summaryCreated)
        assertEquals("ai.keyword.quarantined", properties.topics.keywordQuarantined)
        assertEquals("notification.routing.dlt", properties.dlt.topic)
        assertEquals(3, properties.retry.maxAttempts)
        assertEquals(Duration.ofSeconds(1), properties.retry.backoff)
        assertEquals(
            mapOf(NotificationChannel.SLACK to "admin", NotificationChannel.TELEGRAM to "admin-chat"),
            properties.admin.recipients,
        )
        assertEquals("http://localhost:8080", properties.userService.baseUrl)
        assertEquals("/api/v1/internal/subscriptions", properties.userService.subscriptionsPath)
        assertEquals(Duration.ofSeconds(3), properties.userService.timeout)
        assertEquals(262144, properties.userService.maxInMemorySize)
    }

    @Test
    @DisplayName("필수 값 공백과 범위 위반은 거부한다")
    fun rejectInvalid() {
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(groupId = " ") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(requestTopic = "") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(autoOffsetReset = "") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(summaryCreatedTopic = "") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(keywordQuarantinedTopic = " ") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(dltTopic = "") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(backoff = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(adminRecipients = mapOf(NotificationChannel.SLACK to " ")) }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(subscriptionsPath = "") }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(timeout = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { RoutingTestFixture.properties(maxInMemorySize = 0) }
    }

    @Test
    @DisplayName("user-service base-url이 없으면 기동 실패 메시지로 거부한다")
    fun rejectWithoutBaseUrl() {
        val exception = assertFailsWith<IllegalArgumentException> {
            RoutingTestFixture.properties(baseUrl = "")
        }

        assertEquals(NotificationRoutingProperties.NO_USER_SERVICE_BASE_URL_MESSAGE, exception.message)
    }

    @Test
    @DisplayName("관리자 수신처는 비어 있어도 된다")
    fun allowEmptyAdminRecipients() {
        assertTrue(RoutingTestFixture.properties(adminRecipients = emptyMap()).admin.recipients.isEmpty())
    }
}
