package me.rgunny.kachi.notification.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class NotificationId private constructor(
    val id: UUID
){
    companion object {
        fun newId(): NotificationId = NotificationId(UuidCreator.getTimeOrderedEpoch())

        fun of(id: UUID): NotificationId = NotificationId(id)
    }
}