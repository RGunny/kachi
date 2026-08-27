package me.rgunny.kachi.user.domain

import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.address
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ChannelBinding")
class ChannelBindingTest {
    private val userId = UserId.newId()
    private val now = UserTestFixture.NOW
    private val ttl = Duration.ofMinutes(10)

    @Nested
    @DisplayName("createWithAddress()")
    inner class CreateWithAddress {

        @Test
        @DisplayName("주소가 있는 바인딩은 바로 ACTIVE다")
        fun createActive() {
            val binding = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.SLACK), now)

            assertEquals(ChannelBindingStatus.ACTIVE, binding.status)
            assertEquals(SubscriptionChannel.SLACK, binding.channel)
            assertEquals(address(SubscriptionChannel.SLACK), binding.address)
            assertEquals(now, binding.createdAt)
            assertEquals(now, binding.boundAt)
            assertNull(binding.linkTokenHash)
            assertTrue(binding.isActive)
        }
    }

    @Nested
    @DisplayName("createPending()")
    inner class CreatePending {

        @Test
        @DisplayName("토큰 해시와 만료 시각만 가진 PENDING 바인딩이다")
        fun createPending() {
            val token = LinkToken.issue(now, ttl)

            val binding = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, token, now)

            assertEquals(ChannelBindingStatus.PENDING, binding.status)
            assertNull(binding.address)
            assertNull(binding.boundAt)
            assertEquals(token.hash(), binding.linkTokenHash)
            assertEquals(token.expiresAt, binding.linkTokenExpiresAt)
        }
    }

    @Nested
    @DisplayName("bindAddress()")
    inner class BindAddress {

        @Test
        @DisplayName("주소를 바꾼 새 바인딩을 돌려주고 원본은 바뀌지 않는다")
        fun replaceImmutably() {
            val binding = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.SLACK), now)
            val newAddress = ChannelAddress.of(SubscriptionChannel.SLACK, "https://hooks.slack.com/services/NEW")

            val bound = binding.bindAddress(newAddress, now.plus(Duration.ofHours(1)))

            assertEquals(newAddress, bound.address)
            assertEquals(now.plus(Duration.ofHours(1)), bound.boundAt)
            assertEquals(binding.id, bound.id)
            assertEquals(address(SubscriptionChannel.SLACK), binding.address)
        }

        @Test
        @DisplayName("해지된 바인딩을 되살리면 해지 시각이 지워진다")
        fun reviveRevoked() {
            val revoked = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.SLACK), now).revoke(now)

            val revived = revoked.bindAddress(address(SubscriptionChannel.SLACK), now.plus(Duration.ofHours(1)))

            assertEquals(ChannelBindingStatus.ACTIVE, revived.status)
            assertNull(revived.revokedAt)
        }

        @Test
        @DisplayName("다른 채널의 주소는 받지 않는다")
        fun rejectOtherChannelAddress() {
            val binding = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.SLACK), now)

            assertFailsWith<IllegalArgumentException> {
                binding.bindAddress(address(SubscriptionChannel.DISCORD), now)
            }
        }
    }

    @Nested
    @DisplayName("issueLinkToken() / completeLink()")
    inner class Link {

        @Test
        @DisplayName("토큰을 발급하면 주소를 버리고 PENDING이 된다")
        fun issueDropsAddress() {
            val binding = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.TELEGRAM), now)

            val pending = binding.issueLinkToken(LinkToken.issue(now, ttl))

            assertEquals(ChannelBindingStatus.PENDING, pending.status)
            assertNull(pending.address)
            assertEquals(binding.id, pending.id)
        }

        @Test
        @DisplayName("만료 전에 토큰이 돌아오면 ACTIVE가 되고 토큰은 지운다")
        fun completeBeforeExpiry() {
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, LinkToken.issue(now, ttl), now)
            val boundAt = now.plus(Duration.ofMinutes(9))

            val active = pending.completeLink(address(SubscriptionChannel.TELEGRAM), boundAt)

            assertEquals(ChannelBindingStatus.ACTIVE, active.status)
            assertEquals(boundAt, active.boundAt)
            assertNull(active.linkTokenHash)
            assertNull(active.linkTokenExpiresAt)
        }

        @Test
        @DisplayName("만료 시각부터는 완료할 수 없다")
        fun rejectExpired() {
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, LinkToken.issue(now, ttl), now)

            assertTrue(pending.isLinkTokenExpired(now.plus(ttl)))
            assertFailsWith<IllegalArgumentException> {
                pending.completeLink(address(SubscriptionChannel.TELEGRAM), now.plus(ttl))
            }
        }

        @Test
        @DisplayName("PENDING이 아니면 완료할 수 없다")
        fun rejectNonPending() {
            val active = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.TELEGRAM), now)

            assertTrue(active.isLinkTokenExpired(now))
            assertFailsWith<IllegalArgumentException> {
                active.completeLink(address(SubscriptionChannel.TELEGRAM), now)
            }
        }
    }

    @Nested
    @DisplayName("revoke()")
    inner class Revoke {

        @Test
        @DisplayName("주소와 토큰을 지우고 REVOKED가 된다")
        fun revokeClearsAddress() {
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, LinkToken.issue(now, ttl), now)

            val revoked = pending.revoke(now.plus(Duration.ofHours(1)))

            assertEquals(ChannelBindingStatus.REVOKED, revoked.status)
            assertNull(revoked.address)
            assertNull(revoked.linkTokenHash)
            assertEquals(now.plus(Duration.ofHours(1)), revoked.revokedAt)
            assertEquals(false, revoked.isActive)
        }

        @Test
        @DisplayName("이미 해지된 바인딩은 다시 해지할 수 없다")
        fun rejectRevokingTwice() {
            val revoked = ChannelBinding.createWithAddress(userId, address(SubscriptionChannel.SLACK), now).revoke(now)

            assertFailsWith<IllegalArgumentException> { revoked.revoke(now) }
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("상태와 주소·토큰이 어긋난 조합은 복원할 수 없다")
        fun rejectInconsistentState() {
            assertFailsWith<IllegalArgumentException> {
                ChannelBinding.restore(
                    id = ChannelBindingId.newId(),
                    userId = userId,
                    channel = SubscriptionChannel.SLACK,
                    status = ChannelBindingStatus.REVOKED,
                    address = address(SubscriptionChannel.SLACK),
                    linkTokenHash = null,
                    linkTokenExpiresAt = null,
                    createdAt = now,
                    boundAt = now,
                    revokedAt = now
                )
            }
        }
    }
}
