package me.rgunny.kachi.user.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * 채널 바인딩 식별자.
 *
 * 저장소 안에서만 쓴다. 알림 쪽은 바인딩을 `(userId, channel)`로 가리키므로 이 값이 밖으로 나가지 않는다.
 */
@JvmInline
value class ChannelBindingId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): ChannelBindingId = ChannelBindingId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): ChannelBindingId = ChannelBindingId(value)
    }
}
