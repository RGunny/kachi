package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.ai.contract.AiKeywordQuarantinedEvent
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteQuarantineCommand

/**
 * eventKey는 quarantineId와 격리 시각을 합친 값이다.
 * quarantineId만 쓰면 같은 키워드의 재격리가 걸러진다.
 */
object AiKeywordQuarantinedEventMapper {

    fun toCommand(event: AiKeywordQuarantinedEvent): RouteQuarantineCommand {
        require(event.schemaVersion == AiKeywordQuarantinedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported ai keyword quarantined schemaVersion=${event.schemaVersion}"
        }

        return RouteQuarantineCommand(
            eventKey = eventKey(event),
            keyword = event.keyword,
            message = AiNotificationMessageRenderer.render(event),
        )
    }

    fun eventKey(event: AiKeywordQuarantinedEvent): String {
        return "${event.quarantineId}:${event.quarantinedAt.toEpochMilli()}"
    }
}
