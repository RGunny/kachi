package me.rgunny.kachi.ai.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * story 요약 트리거와 발행 스위치 설정.
 *
 * [eventsEnabled]는 story 경로 이벤트(요약 v2·분리 요청·story 격리)를 outbox에 남길지 정한다.
 * 값 검증은 이 값들로 만들어지는 [me.rgunny.kachi.ai.application.service.story.StorySummaryPolicy]가 맡는다.
 */
@ConfigurationProperties(prefix = StorySummaryProperties.PREFIX)
data class StorySummaryProperties(
    val minNewArticles: Int,
    val maxWait: Duration,
    val maxArticlesPerVersion: Int,
    val eventsEnabled: Boolean
) {
    companion object {
        const val PREFIX = "kachi.ai.story-summary"
    }
}
