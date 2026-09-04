package me.rgunny.kachi.e2e

import me.rgunny.kachi.ai.contract.AiKeywordQuarantinedEvent
import me.rgunny.kachi.e2e.support.KachiCluster
import me.rgunny.kachi.e2e.support.KafkaSupport
import me.rgunny.kachi.e2e.support.NotificationStore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 키워드 격리 알림이 관리자에게 가는 사이클을 실제 서비스 다섯 개 위에서 돈다.
 *
 * ai-service가 같은 키워드에서 임계치만큼 실패하면 `ai.keyword.quarantined`를 발행하고, routing은 구독자 대신
 * user-service의 관리자 목록(`/internal/users?role=ADMIN`)으로 fan-out한다. 관리자는 local seed가 만든 계정이고 채널 바인딩 셋을 갖는다.
 */
class KeywordQuarantineNotificationE2ETest : KachiCycleE2ETestBase() {

    @Test
    @DisplayName("키워드가 격리되면 관리자가 바인딩한 채널마다 격리 알림이 발송된다")
    fun deliverQuarantineToAdmins() {
        val keyword = uniqueKeyword("broken")
        val admins = users.admins()
        assertTrue(admins.isNotEmpty(), "local seed 관리자가 있어야 한다")
        stubNews(count = 1)
        repeat(KachiCluster.QUARANTINE_FAILURE_THRESHOLD) { cluster.llm.enqueueClientError() }

        // tick은 동기라 돌아오면 실행 겹침 가드가 이미 풀려 있다. 같은 키워드로 임계치만큼 연속 실패시킨다.
        repeat(KachiCluster.QUARANTINE_FAILURE_THRESHOLD) { summarize(keyword) }

        val record = kafka.recordsOf(KafkaSupport.TOPIC_KEYWORD_QUARANTINED, expected = 1) { it.key() == keyword }.single()
        val event = http.jsonMapper.readValue(record.value(), AiKeywordQuarantinedEvent::class.java)
        assertEquals(KachiCluster.QUARANTINE_FAILURE_THRESHOLD, event.consecutiveFailures)

        admins.forEach { (adminId, channels) ->
            val sent = notifications.awaitSentChannels(adminId, requestIdPrefix = "qrt:", expectedCount = channels.size)
            assertEquals(channels.toSet(), sent, "admin $adminId")
        }
    }
}
