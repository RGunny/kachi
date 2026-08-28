package me.rgunny.kachi.notification.worker.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationRecipientProperties")
class NotificationRecipientPropertiesTest {

    @Test
    @DisplayName("유효한 값으로 만들어진다")
    fun valid() {
        val properties = properties()

        assertEquals(Duration.ofMinutes(5), properties.cacheTtl)
        assertEquals("http://localhost:8080", properties.userService.baseUrl)
    }

    @Test
    @DisplayName("user-service base-url이 비어 있으면 거부한다")
    fun rejectBlankBaseUrl() {
        val exception = assertFailsWith<IllegalArgumentException> { properties(baseUrl = " ") }

        assertEquals(NotificationRecipientProperties.NO_USER_SERVICE_BASE_URL_MESSAGE, exception.message)
    }

    @Test
    @DisplayName("cache-ttl이 양수가 아니면 거부한다")
    fun rejectNonPositiveCacheTtl() {
        assertFailsWith<IllegalArgumentException> { properties(cacheTtl = Duration.ZERO) }
    }

    @Test
    @DisplayName("user-service timeout이 양수가 아니면 거부한다")
    fun rejectNonPositiveTimeout() {
        assertFailsWith<IllegalArgumentException> { properties(timeout = Duration.ofSeconds(-1)) }
    }

    @Test
    @DisplayName("channel-binding-path에 {userId}와 {channel}이 없으면 거부한다")
    fun rejectPathWithoutPlaceholders() {
        assertFailsWith<IllegalArgumentException> { properties(channelBindingPath = "/api/v1/internal/users/{userId}/channel-bindings") }
        assertFailsWith<IllegalArgumentException> { properties(channelBindingPath = "/api/v1/internal/channel-bindings/{channel}") }
    }

    @Test
    @DisplayName("max-in-memory-size가 양수가 아니면 거부한다")
    fun rejectNonPositiveMaxInMemorySize() {
        assertFailsWith<IllegalArgumentException> { properties(maxInMemorySize = 0) }
    }

    private fun properties(
        cacheTtl: Duration = Duration.ofMinutes(5),
        baseUrl: String = "http://localhost:8080",
        channelBindingPath: String = "/api/v1/internal/users/{userId}/channel-bindings/{channel}",
        timeout: Duration = Duration.ofSeconds(3),
        maxInMemorySize: Int = 256 * 1024,
    ): NotificationRecipientProperties {
        return NotificationRecipientProperties(
            cacheTtl = cacheTtl,
            userService = NotificationRecipientProperties.UserService(
                baseUrl = baseUrl,
                channelBindingPath = channelBindingPath,
                timeout = timeout,
                maxInMemorySize = maxInMemorySize,
            ),
        )
    }
}
