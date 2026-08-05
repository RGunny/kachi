package me.rgunny.kachi.notification.domain

import java.nio.charset.StandardCharsets
import java.util.UUID

@JvmInline
value class NotificationDltMessageId private constructor(
    val id: UUID,
) {
    companion object {
        fun of(id: UUID): NotificationDltMessageId = NotificationDltMessageId(id)

        /**
         * Kafka 원본 record 위치로 deterministic id를 만든다.
         *
         * DLT consumer가 같은 DLT record를 다시 처리해도 같은 document를 upsert하기 위함이다.
         */
        fun fromOriginalRecord(
            topic: String,
            partition: Int,
            offset: Long,
        ): NotificationDltMessageId {
            require(topic.isNotBlank()) { "topic must not be blank" }
            require(partition >= 0) { "partition must not be negative" }
            require(offset >= 0) { "offset must not be negative" }

            val source = "$topic:$partition:$offset".toByteArray(StandardCharsets.UTF_8)
            return NotificationDltMessageId(UUID.nameUUIDFromBytes(source))
        }
    }
}
