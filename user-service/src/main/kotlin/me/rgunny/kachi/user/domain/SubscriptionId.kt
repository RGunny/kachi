package me.rgunny.kachi.user.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * 구독 식별자.
 *
 * 시간순 UUID(v7)로 만들어 삽입 순서와 인덱스 순서가 같다.
 */
@JvmInline
value class SubscriptionId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): SubscriptionId = SubscriptionId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): SubscriptionId = SubscriptionId(value)
    }
}
