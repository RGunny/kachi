package me.rgunny.kachi.notification.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class NotificationHistoryId private constructor(
    val id: UUID
){
    companion object {
        fun newId(): NotificationHistoryId = NotificationHistoryId(UuidCreator.getTimeOrderedEpoch())

        fun of(id: UUID): NotificationHistoryId = NotificationHistoryId(id)
    }
}