package me.rgunny.kachi.collector.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class CollectionRunId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): CollectionRunId = CollectionRunId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): CollectionRunId = CollectionRunId(value)
    }
}
