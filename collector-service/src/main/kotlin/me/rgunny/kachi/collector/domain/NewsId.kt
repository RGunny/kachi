package me.rgunny.kachi.collector.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class NewsId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): NewsId = NewsId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): NewsId = NewsId(value)
    }
}
