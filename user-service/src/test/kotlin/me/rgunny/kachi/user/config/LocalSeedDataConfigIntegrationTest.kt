package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.outbound.persistence.ChannelBindingJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.KeywordJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.user.adapter.outbound.persistence.SubscriptionJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.UserJpaRepository
import me.rgunny.kachi.user.application.port.outbound.binding.AddressCipherPort
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 시드 runner를 실제 저장소 위에서 돌려 관리자 바인딩 주소가 설정값을 따르는지 본다.
 */
@DisplayName("LocalSeedDataConfig 통합 테스트")
class LocalSeedDataConfigIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var channelBindingJpaRepository: ChannelBindingJpaRepository

    @Autowired
    private lateinit var keywordJpaRepository: KeywordJpaRepository

    @Autowired
    private lateinit var subscriptionJpaRepository: SubscriptionJpaRepository

    @Autowired
    private lateinit var addressCipherPort: AddressCipherPort

    @Test
    @DisplayName("설정된 관리자 주소로 세 채널 바인딩을 심고 시드 사용자는 자리표시 주소를 쓴다")
    fun seedAdminBindingsFromConfiguredAddresses() {
        runSeed(configured(), UserTestFixture.CLOCK)
        flushAndClear()

        assertEquals(CONFIGURED_SLACK, adminBinding(SubscriptionChannel.SLACK).address?.value)
        assertEquals(CONFIGURED_DISCORD, adminBinding(SubscriptionChannel.DISCORD).address?.value)
        assertEquals(CONFIGURED_TELEGRAM, adminBinding(SubscriptionChannel.TELEGRAM).address?.value)
        assertTrue(SubscriptionChannel.entries.all { adminBinding(it).status == ChannelBindingStatus.ACTIVE })

        val seedUser = assertNotNull(userJpaRepository.findByEmail(SEED_USER_EMAIL))
        val seedBinding = assertNotNull(channelBindingJpaRepository.findByUserIdAndChannel(seedUser.id, SubscriptionChannel.SLACK))
        assertEquals(PLACEHOLDER_SLACK, seedBinding.toDomain(addressCipherPort).address?.value)
    }

    @Test
    @DisplayName("관리자는 시드 키워드 전부를 세 채널로 구독하고 다시 실행해도 늘지 않는다")
    fun seedAdminSubscriptions() {
        runSeed(configured(), UserTestFixture.CLOCK)
        runSeed(configured(), UserTestFixture.CLOCK)

        val admin = userJpaRepository.findAllByRole(UserRole.ADMIN).single()
        val subscriptions = subscriptionJpaRepository.findAllByUserId(admin.id).map { it.toDomain() }

        assertEquals(keywordJpaRepository.findAll().size, subscriptions.size)
        assertTrue(subscriptions.all { it.enabled && it.channels == SubscriptionChannel.entries.toSet() })
    }

    @Test
    @DisplayName("설정이 비어 있으면 자리표시 주소로 심는다")
    fun fallbackToPlaceholderWhenBlank() {
        runSeed(UserSeedProperties(), UserTestFixture.CLOCK)
        flushAndClear()

        assertEquals(PLACEHOLDER_SLACK, adminBinding(SubscriptionChannel.SLACK).address?.value)
        assertEquals("https://discord.com/api/webhooks/000000/LOCAL-SEED", adminBinding(SubscriptionChannel.DISCORD).address?.value)
        assertEquals("000000000", adminBinding(SubscriptionChannel.TELEGRAM).address?.value)
    }

    @Test
    @DisplayName("이미 있는 관리자 바인딩의 주소가 설정값과 다르면 설정값으로 바꾼다")
    fun rebindWhenConfiguredAddressDiffers() {
        runSeed(UserSeedProperties(), UserTestFixture.CLOCK)
        flushAndClear()
        val later = Clock.fixed(UserTestFixture.NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)

        runSeed(configured(), later)
        flushAndClear()

        val binding = adminBinding(SubscriptionChannel.SLACK)
        assertEquals(CONFIGURED_SLACK, binding.address?.value)
        assertEquals(later.instant(), binding.boundAt)
        assertEquals(1, channelBindingJpaRepository.findAllByUserId(binding.userId.value).count { it.channel == SubscriptionChannel.SLACK })
    }

    @Test
    @DisplayName("주소가 같으면 다시 저장하지 않아 boundAt이 그대로다")
    fun keepBindingWhenAddressIsSame() {
        runSeed(configured(), UserTestFixture.CLOCK)
        flushAndClear()
        val later = Clock.fixed(UserTestFixture.NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)

        runSeed(configured(), later)
        flushAndClear()

        assertEquals(UserTestFixture.NOW, adminBinding(SubscriptionChannel.SLACK).boundAt)
    }

    @Test
    @DisplayName("설정된 주소의 형식이 채널과 맞지 않으면 시드가 실패한다")
    fun failOnInvalidConfiguredAddress() {
        val properties = UserSeedProperties(UserSeedProperties.AdminAddresses(slack = "https://example.com/not-slack"))

        assertFailsWith<IllegalArgumentException> { runSeed(properties, UserTestFixture.CLOCK) }
    }

    private fun runSeed(properties: UserSeedProperties, clock: Clock) {
        LocalSeedDataConfig().localSeedDataInitializer(
            userJpaRepository = userJpaRepository,
            channelBindingJpaRepository = channelBindingJpaRepository,
            keywordJpaRepository = keywordJpaRepository,
            subscriptionJpaRepository = subscriptionJpaRepository,
            addressCipherPort = addressCipherPort,
            clock = clock,
            seedProperties = properties
        ).run(DefaultApplicationArguments())
    }

    private fun adminBinding(channel: SubscriptionChannel): ChannelBinding {
        val admin = userJpaRepository.findAllByRole(UserRole.ADMIN).single()
        val entity = assertNotNull(channelBindingJpaRepository.findByUserIdAndChannel(admin.id, channel))
        return entity.toDomain(addressCipherPort)
    }

    private fun configured(): UserSeedProperties {
        return UserSeedProperties(
            UserSeedProperties.AdminAddresses(
                slack = CONFIGURED_SLACK,
                discord = CONFIGURED_DISCORD,
                telegram = CONFIGURED_TELEGRAM
            )
        )
    }

    private companion object {
        const val SEED_USER_EMAIL = "collector-admin@kachi.local"
        const val PLACEHOLDER_SLACK = "https://hooks.slack.com/services/LOCAL/SEED/WEBHOOK"
        const val CONFIGURED_SLACK = "https://hooks.slack.com/services/T111/B222/configured"
        const val CONFIGURED_DISCORD = "https://discord.com/api/webhooks/111111/configured"
        const val CONFIGURED_TELEGRAM = "987654321"
    }
}
