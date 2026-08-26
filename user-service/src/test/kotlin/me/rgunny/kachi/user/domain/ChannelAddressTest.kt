package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@DisplayName("ChannelAddress")
class ChannelAddressTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @ParameterizedTest(name = "{0}: {1}")
        @CsvSource(
            "SLACK, https://hooks.slack.com/services/T000/B000/XXXX",
            "DISCORD, https://discord.com/api/webhooks/1234/abcd",
            "DISCORD, https://discordapp.com/api/webhooks/1234/abcd",
            "TELEGRAM, 123456789",
            "TELEGRAM, -1001234567890"
        )
        @DisplayName("채널 형식에 맞는 주소를 받는다")
        fun acceptValidAddress(channel: SubscriptionChannel, raw: String) {
            val address = ChannelAddress.of(channel, " $raw ")

            assertEquals(raw, address.value)
            assertEquals(channel, address.channel)
        }

        @ParameterizedTest(name = "{0}: {1}")
        @CsvSource(
            "SLACK, https://example.com/hooks.slack.com/x",
            "SLACK, http://hooks.slack.com/services/x",
            "SLACK, https://hooks.slack.com/",
            "DISCORD, https://hooks.slack.com/services/x",
            "TELEGRAM, abc",
            "TELEGRAM, 12 34",
            "TELEGRAM, https://t.me/x"
        )
        @DisplayName("채널 형식에 맞지 않는 주소는 거부한다")
        fun rejectInvalidAddress(channel: SubscriptionChannel, raw: String) {
            assertFalse(ChannelAddress.isValid(channel, raw))
            assertFailsWith<IllegalArgumentException> { ChannelAddress.of(channel, raw) }
        }

        @Test
        @DisplayName("빈 값은 거부한다")
        fun rejectBlank() {
            assertFailsWith<IllegalArgumentException> { ChannelAddress.of(SubscriptionChannel.SLACK, "  ") }
        }
    }

    @Nested
    @DisplayName("마스킹")
    inner class Masking {

        @Test
        @DisplayName("URL은 host까지만 남긴다")
        fun maskUrlPath() {
            val address = ChannelAddress.of(SubscriptionChannel.SLACK, "https://hooks.slack.com/services/T000/B000/XXXX")

            assertEquals("https://hooks.slack.com/****", address.masked())
        }

        @Test
        @DisplayName("chat id는 끝 두 자리만 남긴다")
        fun maskChatId() {
            assertEquals("****89", ChannelAddress.of(SubscriptionChannel.TELEGRAM, "123456789").masked())
        }

        @Test
        @DisplayName("toString은 원문을 담지 않는다")
        fun toStringDoesNotExposeValue() {
            val text = ChannelAddress.of(SubscriptionChannel.SLACK, "https://hooks.slack.com/services/T000/B000/XXXX").toString()

            assertFalse(text.contains("T000"))
            assertEquals(true, text.contains("hooks.slack.com"))
        }
    }
}
