package me.rgunny.kachi.user.domain

import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@DisplayName("LinkToken")
class LinkTokenTest {
    private val now = UserTestFixture.NOW

    @Test
    @DisplayName("발급할 때마다 다른 base64url 값과 ttl만큼 뒤의 만료 시각을 가진다")
    fun issueRandomToken() {
        val first = LinkToken.issue(now, Duration.ofMinutes(10))
        val second = LinkToken.issue(now, Duration.ofMinutes(10))

        assertNotEquals(first.value, second.value)
        assertEquals(43, first.value.length)
        assertTrue(first.value.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(now.plus(Duration.ofMinutes(10)), first.expiresAt)
    }

    @Test
    @DisplayName("해시는 같은 원문에서 같고 32바이트다")
    fun hashIsDeterministic() {
        val token = LinkToken.issue(now, Duration.ofMinutes(10))

        assertEquals(token.hash(), LinkTokenHash.of(token.value))
        assertEquals(32, token.hash().value.size)
        assertNotEquals(token.hash(), LinkTokenHash.of(token.value + "x"))
    }

    @Test
    @DisplayName("ttl이 0 이하이면 발급할 수 없다")
    fun rejectNonPositiveTtl() {
        assertFailsWith<IllegalArgumentException> { LinkToken.issue(now, Duration.ZERO) }
    }

    @Test
    @DisplayName("toString은 토큰 원문을 담지 않는다")
    fun toStringDoesNotExposeValue() {
        val token = LinkToken.issue(now, Duration.ofMinutes(10))

        assertFalse(token.toString().contains(token.value))
        assertFalse(token.hash().toString().contains(token.value))
    }
}
