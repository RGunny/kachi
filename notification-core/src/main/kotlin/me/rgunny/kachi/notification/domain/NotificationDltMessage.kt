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
    val dltTopic: String,
    val dltPartition: Int,
    val dltOffset: Long,
    val consumerGroup: String?,
    val messageKey: String?,
    val payload: String,
    val exceptionFqcn: String?,
    val exceptionMessage: String?,
    val failedAt: Instant,
    val receivedAt: Instant,
    status: NotificationDltMessageStatus,
) {
    var status: NotificationDltMessageStatus = status
        private set

    companion object {
        fun record(
            originalTopic: String,
            originalPartition: Int,
            originalOffset: Long,
            dltTopic: String,
            dltPartition: Int,
            dltOffset: Long,
            consumerGroup: String?,
            messageKey: String?,
            payload: String,
            exceptionFqcn: String?,
            exceptionMessage: String?,
            failedAt: Instant,
            receivedAt: Instant,
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
                dltTopic = dltTopic,
                dltPartition = dltPartition,
                dltOffset = dltOffset,
                consumerGroup = consumerGroup,
                messageKey = messageKey,
                payload = payload,
                exceptionFqcn = exceptionFqcn,
                exceptionMessage = exceptionMessage,
                failedAt = failedAt,
                receivedAt = receivedAt,
                status = NotificationDltMessageStatus.PENDING,
            )
        }

        fun restore(
            id: NotificationDltMessageId,
            originalTopic: String,
            originalPartition: Int,
            originalOffset: Long,
            dltTopic: String,
            dltPartition: Int,
            dltOffset: Long,
            consumerGroup: String?,
            messageKey: String?,
            payload: String,
            exceptionFqcn: String?,
            exceptionMessage: String?,
            failedAt: Instant,
            receivedAt: Instant,
            status: NotificationDltMessageStatus,
        ): NotificationDltMessage {
            return NotificationDltMessage(
                id = id,
                originalTopic = originalTopic,
                originalPartition = originalPartition,
                originalOffset = originalOffset,
                dltTopic = dltTopic,
                dltPartition = dltPartition,
                dltOffset = dltOffset,
                consumerGroup = consumerGroup,
                messageKey = messageKey,
                payload = payload,
                exceptionFqcn = exceptionFqcn,
                exceptionMessage = exceptionMessage,
                failedAt = failedAt,
                receivedAt = receivedAt,
                status = status,
            )
        }
    }
}
