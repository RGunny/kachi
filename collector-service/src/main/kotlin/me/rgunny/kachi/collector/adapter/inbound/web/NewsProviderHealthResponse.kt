package me.rgunny.kachi.collector.adapter.inbound.web

import me.rgunny.kachi.collector.application.port.outbound.news.model.CollectedArticle
import me.rgunny.kachi.collector.domain.NewsSource
import java.time.Instant

data class NewsProviderHealthResponse(
    val source: String,
    val keyword: String,
    val fetchedCount: Int,
    val samples: List<CollectedArticleSampleResponse>
) {
    companion object {

        fun from(
            source: NewsSource,
            keyword: String,
            articles: List<CollectedArticle>
        ): NewsProviderHealthResponse {
            return NewsProviderHealthResponse(
                source = source.name,
                keyword = keyword,
                fetchedCount = articles.size,
                samples = articles.take(SAMPLE_SIZE).map(CollectedArticleSampleResponse::from)
            )
        }

        private const val SAMPLE_SIZE = 5
    }
}

data class CollectedArticleSampleResponse(
    val title: String,
    val url: String,
    val publishedAt: Instant?
) {
    companion object {

        fun from(article: CollectedArticle): CollectedArticleSampleResponse {
            return CollectedArticleSampleResponse(
                title = article.title,
                url = article.url,
                publishedAt = article.publishedAt
            )
        }
    }
}
