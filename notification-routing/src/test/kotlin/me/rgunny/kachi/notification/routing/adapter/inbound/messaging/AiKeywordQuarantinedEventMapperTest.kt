package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("AiKeywordQuarantinedEventMapper")
class AiKeywordQuarantinedEventMapperTest {

    @Test
    @DisplayName("eventKey는 quarantineId와 격리 시각 epoch millis를 합친 값이다")
    fun toCommand() {
        val command = AiKeywordQuarantinedEventMapper.toCommand(RoutingTestFixture.keywordQuarantinedEvent())

        assertEquals("quarantine-1:${RoutingTestFixture.NOW.toEpochMilli()}", command.eventKey)
        assertEquals("tesla", command.keyword)
        assertTrue(command.message.startsWith("[키워드 격리] 'tesla'"))
    }

    @Test
    @DisplayName("schemaVersion이 다르면 거부한다")
    fun rejectSchemaVersion() {
        assertFailsWith<IllegalArgumentException> {
            AiKeywordQuarantinedEventMapper.toCommand(RoutingTestFixture.keywordQuarantinedEvent(schemaVersion = 0))
        }
    }
}
