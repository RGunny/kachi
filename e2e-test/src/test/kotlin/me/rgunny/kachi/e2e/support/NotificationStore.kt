package me.rgunny.kachi.e2e.support

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import org.bson.Document
import java.time.Duration
import java.time.Instant

/**
 * notification-service·worker가 공유하는 `notifications` 컬렉션을 읽는다.
 *
 * 알림의 최종 상태(SENT·SUPPRESSED)는 여기에만 있다. requestId로 조회하는 API(Q1)가 생기면 이 직접 조회를 그 API로 바꾼다.
 */
class NotificationStore(mongoUri: String, database: String) : AutoCloseable {
    private val client: MongoClient = MongoClients.create(mongoUri)
    private val notifications = client.getDatabase(database).getCollection(COLLECTION)
    private val dltMessages = client.getDatabase(database).getCollection(DLT_COLLECTION)

    fun statusOf(requestId: String): String? {
        return notifications.find(Filters.eq("requestId", requestId)).first()?.getString("status")
    }

    /** [requestId]의 알림이 [expected] 상태가 될 때까지 기다린다. 끝 상태가 아니면 마지막으로 본 상태를 담아 실패시킨다. */
    fun awaitStatus(requestId: String, expected: String, timeout: Duration = DEFAULT_TIMEOUT): String {
        val deadline = Instant.now().plus(timeout)
        var last: String? = null
        while (Instant.now().isBefore(deadline)) {
            last = statusOf(requestId)
            if (last == expected) return last
            if (last in TERMINAL_STATUSES) break
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
        error("notification $requestId expected $expected but was $last; dlt=${dltSummary()}")
    }

    /** worker가 DLT에 남긴 메시지의 예외 요약. 발송이 끝나지 않은 이유를 실패 메시지에 싣는다. */
    fun dltSummary(): List<String> {
        return dltMessages.find().map { "${it.getString("exceptionFqcn")}: ${it.getString("exceptionMessage")}" }.toList()
    }

    /**
     * 수신자의 알림 중 requestId가 [requestIdPrefix]로 시작하는 것이 [expectedCount]개 `SENT`가 될 때까지 기다려 그 채널 집합을 돌려준다.
     * 시간 안에 차지 않으면 그때까지 본 채널과 DLT 요약을 담아 실패시킨다.
     */
    fun awaitSentChannels(
        recipientId: String,
        requestIdPrefix: String,
        expectedCount: Int,
        timeout: Duration = DEFAULT_TIMEOUT
    ): Set<String> {
        val deadline = Instant.now().plus(timeout)
        var sent = emptySet<String>()
        while (Instant.now().isBefore(deadline)) {
            sent = findByRecipient(recipientId, requestIdPrefix)
                .filter { it.getString("status") == STATUS_SENT }
                .map { it.getString("channel") }
                .toSet()
            if (sent.size >= expectedCount) return sent
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
        error("recipient $recipientId expected $expectedCount sent notifications with prefix $requestIdPrefix but saw $sent; dlt=${dltSummary()}")
    }

    /** 수신자의 알림 중 requestId가 [requestIdPrefix]로 시작하는 것을 돌려준다. */
    fun findByRecipient(recipientId: String, requestIdPrefix: String): List<Document> {
        return notifications.find(
            Filters.and(
                Filters.eq("recipientId", recipientId),
                Filters.regex("requestId", "^${Regex.escape(requestIdPrefix)}")
            )
        ).toList()
    }

    fun countByRequestIdPrefix(requestIdPrefix: String): Long {
        return notifications.countDocuments(Filters.regex("requestId", "^${Regex.escape(requestIdPrefix)}"))
    }

    override fun close() {
        client.close()
    }

    companion object {
        const val COLLECTION = "notifications"
        const val DLT_COLLECTION = "notification_dlt_messages"
        const val STATUS_SENT = "SENT"
        const val STATUS_SUPPRESSED = "SUPPRESSED"
        private val TERMINAL_STATUSES = setOf(STATUS_SENT, STATUS_SUPPRESSED, "DEAD")
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(60)
        private val POLL_INTERVAL: Duration = Duration.ofMillis(500)
    }
}
