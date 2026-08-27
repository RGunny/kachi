package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.ai.contract.AiSummaryCreatedEvent
import me.rgunny.kachi.notification.routing.application.port.inbound.routing.model.RouteSummaryCommand

object AiSummaryCreatedEventMapper {

    fun toCommand(event: AiSummaryCreatedEvent): RouteSummaryCommand {
        require(event.schemaVersion == AiSummaryCreatedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported ai summary created schemaVersion=${event.schemaVersion}"
        }

        return RouteSummaryCommand(
            summaryId = event.summaryId,
            keyword = event.keyword,
            message = AiNotificationMessageRenderer.render(event),
        )
    }
}
