package me.rgunny.kachi.story.adapter.inbound.messaging

import java.util.UUID
import me.rgunny.kachi.collector.contract.CollectorNewsCollectedEvent
import me.rgunny.kachi.collector.contract.CollectorNewsSource
import me.rgunny.kachi.story.application.port.inbound.assembly.model.AttachArticleCommand
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryKeyword

/**
 * 기사 수집 이벤트 계약을 붙일 기사 명령으로 옮기는 변환기.
 *
 * 계약에 맞지 않는 값은 `IllegalArgumentException`으로 거부한다.
 */
object NewsCollectedEventMapper {

    fun toCommand(event: CollectorNewsCollectedEvent): AttachArticleCommand {
        require(event.schemaVersion == CollectorNewsCollectedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported collector news collected schemaVersion=${event.schemaVersion}"
        }

        return AttachArticleCommand(
            newsId = NewsId.of(UUID.fromString(event.newsId)),
            source = toDomain(event.source),
            title = event.title,
            excerpt = event.excerpt,
            url = event.url,
            language = ArticleLanguage.of(event.language),
            publishedAt = event.publishedAt,
            collectedAt = event.collectedAt,
            matchedKeywords = event.matchedKeywords.map { StoryKeyword.of(it) }
        )
    }

    private fun toDomain(source: CollectorNewsSource): ArticleSource {
        return when (source) {
            CollectorNewsSource.GOOGLE -> ArticleSource.GOOGLE
            CollectorNewsSource.NAVER -> ArticleSource.NAVER
            CollectorNewsSource.FINNHUB -> ArticleSource.FINNHUB
        }
    }
}
