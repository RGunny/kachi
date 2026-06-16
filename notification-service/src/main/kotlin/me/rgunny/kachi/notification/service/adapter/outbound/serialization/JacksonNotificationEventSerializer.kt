package me.rgunny.kachi.notification.service.adapter.outbound.serialization

import me.rgunny.kachi.notification.application.port.dto.NotificationDispatchMessage
import me.rgunny.kachi.notification.application.port.outbound.NotificationEventSerializer
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class JacksonNotificationEventSerializer(
    private val jsonMapper: JsonMapper
) : NotificationEventSerializer {

    override fun serializeDispatch(message: NotificationDispatchMessage): String {
        return jsonMapper.writeValueAsString(
            NotificationDispatchPayload.from(message)
        )
    }
}
