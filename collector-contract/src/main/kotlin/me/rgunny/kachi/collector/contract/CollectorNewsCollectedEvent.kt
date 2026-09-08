package me.rgunny.kachi.collector.contract

import java.time.Instant

/**
 * collector-service가 기사 한 건을 저장했음을 알릴 때 사용하는 이벤트 계약.
 *
 * 기사 내용을 다 싣는다. 소비자는 이 값만으로 처리하고 collector의 저장소나 API를 부르지 않는다.
 * 모든 필드가 항상 있다. 기사는 저장 후 바뀌지 않으므로 같은 `newsId`의 레코드는 언제나 같은 내용이다.
 */
data class CollectorNewsCollectedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val newsId: String,
    val source: CollectorNewsSource,
    val title: String,
    val excerpt: String,
    val url: String,
    val language: String,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<String>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
