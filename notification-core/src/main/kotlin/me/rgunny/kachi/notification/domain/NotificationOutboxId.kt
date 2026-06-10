package me.rgunny.kachi.notification.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class NotificationOutboxId private constructor(
    val id: UUID
){
    companion object {
        fun newId(): NotificationOutboxId = NotificationOutboxId(UuidCreator.getTimeOrderedEpoch())

        fun of(id: UUID): NotificationOutboxId = NotificationOutboxId(id)
    }
}