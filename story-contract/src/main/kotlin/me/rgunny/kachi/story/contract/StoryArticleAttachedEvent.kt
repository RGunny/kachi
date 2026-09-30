package me.rgunny.kachi.story.contract

import java.time.Instant

/**
 * story-service가 기사 한 건을 story에 붙였음을 알릴 때 사용하는 이벤트 계약.
 *
 * 기사 내용과 붙인 뒤 story의 키워드·기사 수를 다 싣는다. 소비자는 이 값만으로 처리하고 story-service나 collector의 저장소를 부르지 않는다.
 * 같은 story의 레코드는 storyId를 키로 같은 파티션에 실려 순서가 지켜진다.
 */
data class StoryArticleAttachedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val storyId: String,
    val newsId: String,
    val title: String,
    val excerpt: String,
    val url: String,
    val source: StoryArticleSource,
    val publishedAt: Instant,
    val storyKeywords: List<String>,
    val storyArticleCount: Int,
    val attachedAt: Instant
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
