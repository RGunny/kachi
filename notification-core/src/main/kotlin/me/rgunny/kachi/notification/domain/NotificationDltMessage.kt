package me.rgunny.kachi.notification.domain

import java.time.Instant

/**
 * Kafka DLT에 도달한 notification dispatch 메시지의 운영용 보관 record.
 */
class NotificationDltMessage private constructor(
    val id: NotificationDltMessageId,
    val originalTopic: String,
    val originalPartition: Int,
    val originalOffset: Long,
    /** 원본 record 시각 */
    val originalTimestamp: Instant?,
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    /** DLT record 시각 */
    val deadLetteredAt: Instant,
    /** worker 저장 시각 */
    val storedAt: Instant,
    /**
     * 운영자가 재처리하지 않기로 판단해 DISCARDED로 종료한 시각.
     */
    val discardedAt: Instant?,
    /**
     * DISCARDED 종료 사유.
     */
    val discardReason: String?,
    /**
     * 운영자가 재처리를 완료해 REPROCESSED로 종료한 시각.
     */
    val reprocessedAt: Instant?,
    /**
     * REPROCESSED 종료 사유.
     */
    val reprocessReason: String?,
    val status: NotificationDltMessageStatus,
) {
    companion object {
        /**
         * DLT record를 운영 확인 대상인 PENDING 메시지로 기록한다.
         *
         * payload, messageKey, exception metadata는 runtime에서 관측한 원본 값을 보존해야 하므로
         * 빈 문자열까지 도메인에서 강하게 차단하지 않는다.
         */
        fun record(
            originalTopic: String,
            originalPartition: Int,
            originalOffset: Long,
            originalTimestamp: Instant?,
            dltTopic: String,
            dltPartition: Int,
            dltOffset: Long,
            consumerGroup: String?,
            messageKey: String?,
            payload: String,
            exceptionFqcn: String?,
            exceptionMessage: String?,
            deadLetteredAt: Instant,
            storedAt: Instant,
        ): NotificationDltMessage {
            require(originalTopic.isNotBlank()) { "originalTopic must not be blank" }
            require(originalPartition >= 0) { "originalPartition must not be negative" }
            require(originalOffset >= 0) { "originalOffset must not be negative" }
            require(dltTopic.isNotBlank()) { "dltTopic must not be blank" }
            require(dltPartition >= 0) { "dltPartition must not be negative" }
            require(dltOffset >= 0) { "dltOffset must not be negative" }
            require(consumerGroup == null || consumerGroup.isNotBlank()) { "consumerGroup must not be blank" }

            return NotificationDltMessage(
                id = NotificationDltMessageId.fromOriginalRecord(
                    topic = originalTopic,
                    partition = originalPartition,
                    offset = originalOffset,
                ),
                originalTopic = originalTopic,
                originalPartition = originalPartition,
                originalOffset = originalOffset,
                originalTimestamp = originalTimestamp,
                dltTopic = dltTopic,
                dltPartition = dltPartition,
                dltOffset = dltOffset,
                consumerGroup = consumerGroup,
                messageKey = messageKey,
                payload = payload,
                exceptionFqcn = exceptionFqcn,
                exceptionMessage = exceptionMessage,
                deadLetteredAt = deadLetteredAt,
                storedAt = storedAt,
                discardedAt = null,
                discardReason = null,
                reprocessedAt = null,
                reprocessReason = null,
                status = NotificationDltMessageStatus.PENDING,
            )
        }

        /**
         * 저장소에 보관된 DLT 메시지 snapshot을 복원한다.
         */
        fun restore(
            id: NotificationDltMessageId,
            originalTopic: String,
            originalPartition: Int,
            originalOffset: Long,
            originalTimestamp: Instant?,
            dltTopic: String,
            dltPartition: Int,
            dltOffset: Long,
            consumerGroup: String?,
            messageKey: String?,
            payload: String,
            exceptionFqcn: String?,
            exceptionMessage: String?,
            deadLetteredAt: Instant,
            storedAt: Instant,
            discardedAt: Instant?,
            discardReason: String?,
            reprocessedAt: Instant?,
            reprocessReason: String?,
            status: NotificationDltMessageStatus,
        ): NotificationDltMessage {
            validateCloseFields(status, discardedAt, discardReason, reprocessedAt, reprocessReason)

            return NotificationDltMessage(
                id = id,
                originalTopic = originalTopic,
                originalPartition = originalPartition,
                originalOffset = originalOffset,
                originalTimestamp = originalTimestamp,
                dltTopic = dltTopic,
                dltPartition = dltPartition,
                dltOffset = dltOffset,
                consumerGroup = consumerGroup,
                messageKey = messageKey,
                payload = payload,
                exceptionFqcn = exceptionFqcn,
                exceptionMessage = exceptionMessage,
                deadLetteredAt = deadLetteredAt,
                storedAt = storedAt,
                discardedAt = discardedAt,
                discardReason = discardReason,
                reprocessedAt = reprocessedAt,
                reprocessReason = reprocessReason,
                status = status,
            )
        }

        private fun validateCloseFields(
            status: NotificationDltMessageStatus,
            discardedAt: Instant?,
            discardReason: String?,
            reprocessedAt: Instant?,
            reprocessReason: String?,
        ) {
            require((discardedAt == null) == (discardReason == null)) {
                "discardedAt and discardReason must be both null or both non-null"
            }
            require((reprocessedAt == null) == (reprocessReason == null)) {
                "reprocessedAt and reprocessReason must be both null or both non-null"
            }
            require(discardReason == null || discardReason.isNotBlank()) { "discardReason must not be blank" }
            require(reprocessReason == null || reprocessReason.isNotBlank()) { "reprocessReason must not be blank" }

            when (status) {
                NotificationDltMessageStatus.PENDING -> {
                    require(discardedAt == null && discardReason == null) {
                        "PENDING dlt message must not have discard information"
                    }
                    require(reprocessedAt == null && reprocessReason == null) {
                        "PENDING dlt message must not have reprocess information"
                    }
                }
                NotificationDltMessageStatus.DISCARDED -> {
                    require(discardedAt != null && discardReason != null) {
                        "DISCARDED dlt message must have discard information"
                    }
                    require(reprocessedAt == null && reprocessReason == null) {
                        "DISCARDED dlt message must not have reprocess information"
                    }
                }
                NotificationDltMessageStatus.REPROCESSED -> {
                    require(reprocessedAt != null && reprocessReason != null) {
                        "REPROCESSED dlt message must have reprocess information"
                    }
                    require(discardedAt == null && discardReason == null) {
                        "REPROCESSED dlt message must not have discard information"
                    }
                }
            }
        }
    }

    /**
     * 운영자가 재처리하지 않기로 판단한 DLT 메시지를 폐기한다.
     */
    fun discard(now: Instant, reason: String): NotificationDltMessage {
        require(reason.isNotBlank()) { "reason must not be blank" }
        check(canDiscard()) { "discard requires PENDING, current=$status" }

        return NotificationDltMessage(
            id = id,
            originalTopic = originalTopic,
            originalPartition = originalPartition,
            originalOffset = originalOffset,
            originalTimestamp = originalTimestamp,
            dltTopic = dltTopic,
            dltPartition = dltPartition,
            dltOffset = dltOffset,
            consumerGroup = consumerGroup,
            messageKey = messageKey,
            payload = payload,
            exceptionFqcn = exceptionFqcn,
            exceptionMessage = exceptionMessage,
            deadLetteredAt = deadLetteredAt,
            storedAt = storedAt,
            discardedAt = now,
            discardReason = reason,
            reprocessedAt = reprocessedAt,
            reprocessReason = reprocessReason,
            status = NotificationDltMessageStatus.DISCARDED,
        )
    }

    fun canDiscard(): Boolean {
        return status == NotificationDltMessageStatus.PENDING
    }
}
