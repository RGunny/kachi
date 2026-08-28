package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.domain.SubscriptionChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("UserSeedProperties")
class UserSeedPropertiesTest {

    @ParameterizedTest
    @EnumSource(SubscriptionChannel::class)
    @DisplayName("설정이 없으면 모든 채널의 관리자 주소가 null이다")
    fun defaultIsNull(channel: SubscriptionChannel) {
        assertNull(UserSeedProperties().adminAddresses.of(channel))
    }

    @Test
    @DisplayName("공백뿐인 값은 null이고 앞뒤 공백은 잘라 돌려준다")
    fun blankIsNullAndTrimmed() {
        val addresses = UserSeedProperties.AdminAddresses(
            slack = "  https://hooks.slack.com/services/T/B/X  ",
            discord = "   ",
            telegram = "123456"
        )

        assertEquals("https://hooks.slack.com/services/T/B/X", addresses.of(SubscriptionChannel.SLACK))
        assertNull(addresses.of(SubscriptionChannel.DISCORD))
        assertEquals("123456", addresses.of(SubscriptionChannel.TELEGRAM))
    }
}
