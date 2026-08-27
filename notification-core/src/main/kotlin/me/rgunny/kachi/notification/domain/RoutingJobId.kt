package me.rgunny.kachi.notification.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class RoutingJobId private constructor(
    val id: UUID
) {
    companion object {
        fun newId(): RoutingJobId = RoutingJobId(UuidCreator.getTimeOrderedEpoch())

        fun of(id: UUID): RoutingJobId = RoutingJobId(id)
    }
}
