package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("AiSummaryCreatedEventMapper")
class AiSummaryCreatedEventMapperTest {

    @Test
    @DisplayName("이벤트를 routing command로 옮긴다")
    fun toCommand() {
        val command = AiSummaryCreatedEventMapper.toCommand(RoutingTestFixture.summaryCreatedEvent())

        assertEquals("summary-1", command.summaryId)
        assertEquals("tesla", command.keyword)
        assertTrue(command.message.startsWith("[tesla] Tesla opens new plant"))
    }

    @Test
    @DisplayName("schemaVersion이 다르면 거부한다")
    fun rejectSchemaVersion() {
        assertFailsWith<IllegalArgumentException> {
            AiSummaryCreatedEventMapper.toCommand(RoutingTestFixture.summaryCreatedEvent(schemaVersion = 2))
        }
    }
}
