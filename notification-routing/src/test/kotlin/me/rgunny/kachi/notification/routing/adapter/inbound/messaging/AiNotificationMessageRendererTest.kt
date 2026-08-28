package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("AiNotificationMessageRenderer")
class AiNotificationMessageRendererTest {

    @Test
    @DisplayName("요약 본문은 키워드·제목·내용·감성·기사 수·시각 순이다")
    fun renderSummary() {
        val message = AiNotificationMessageRenderer.render(RoutingTestFixture.summaryCreatedEvent())

        assertEquals(
            "[tesla] Tesla opens new plant\n\nTesla announced a new plant.\n\n감성 POSITIVE · 기사 3건 · 2026-06-13T00:00:00Z",
            message,
        )
    }

    @Test
    @DisplayName("격리 본문은 관리자 표시와 키워드·종류·실패 횟수·사유·시각을 담는다")
    fun renderQuarantine() {
        val message = AiNotificationMessageRenderer.render(RoutingTestFixture.keywordQuarantinedEvent())

        assertEquals(
            "[관리자] 키워드 'tesla' 격리 — NEWS_SUMMARY, 연속 실패 3회, 사유 INVALID_RESPONSE, 2026-06-13T00:00:00Z",
            message,
        )
    }

    @Test
    @DisplayName("2000자를 넘으면 잘라내고 끝에 말줄임을 붙인다")
    fun truncate() {
        val message = AiNotificationMessageRenderer.render(
            RoutingTestFixture.summaryCreatedEvent(content = "x".repeat(3000))
        )

        assertEquals(AiNotificationMessageRenderer.MAX_LENGTH, message.length)
        assertTrue(message.endsWith("…"))
    }
}
