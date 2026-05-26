package me.rgunny.kachi.user.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class UserId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): UserId = UserId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): UserId = UserId(value)
    }
}
