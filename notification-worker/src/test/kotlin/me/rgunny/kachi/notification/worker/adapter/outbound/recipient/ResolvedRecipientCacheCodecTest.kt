package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("ResolvedRecipientCacheCodec")
class ResolvedRecipientCacheCodecTest {

    private val codec = ResolvedRecipientCacheCodec(JsonMapper.builder().build())

    @Test
    @DisplayName("Available은 address와 함께 JSON으로 왕복한다")
    fun availableRoundTrip() {
        val resolved = AvailableRecipient("https://hooks.slack.test/services/T000/B000/XXXX")

        val encoded = codec.encode(resolved)

        assertEquals("""{"available":true,"address":"https://hooks.slack.test/services/T000/B000/XXXX","reason":null}""", encoded)
        assertEquals(resolved, codec.decode(encoded))
    }

    @ParameterizedTest
    @EnumSource(RecipientUnavailableReason::class)
    @DisplayName("Unavailable은 reason과 함께 JSON으로 왕복한다")
    fun unavailableRoundTrip(reason: RecipientUnavailableReason) {
        val resolved = UnavailableRecipient(reason)

        assertEquals(resolved, codec.decode(codec.encode(resolved)))
    }

    @Test
    @DisplayName("깨진 JSON은 예외다")
    fun malformed() {
        assertFailsWith<Exception> { codec.decode("not-json") }
    }
}
