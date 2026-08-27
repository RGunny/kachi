package me.rgunny.kachi.user.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * 채널 바인딩 식별자.
 *
 * 알림 라우팅이 수신처를 가리키는 recipientRef가 이 값이다.
 * 주소가 바뀌어도 id는 바뀌지 않는다.
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
